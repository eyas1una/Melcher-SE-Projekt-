package com.group_2.service.finance;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.group_2.model.User;
import com.group_2.model.WG;
import com.group_2.model.finance.StandingOrder;
import com.group_2.model.finance.StandingOrderFrequency;
import com.group_2.repository.UserRepository;
import com.group_2.repository.finance.StandingOrderRepository;
import com.group_2.dto.finance.FinanceMapper;
import com.group_2.dto.finance.StandingOrderDTO;
import com.group_2.dto.finance.StandingOrderViewDTO;
import com.group_2.repository.WGRepository;
import com.group_2.util.MonthlyScheduleUtil;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Service
public class StandingOrderService {

    private static final Logger log = LoggerFactory.getLogger(StandingOrderService.class);

    private final StandingOrderRepository standingOrderRepository;
    private final TransactionService transactionService;
    private final ObjectMapper objectMapper;
    private final FinanceMapper financeMapper;
    private final UserRepository userRepository;
    private final WGRepository wgRepository;

    @Autowired
    public StandingOrderService(StandingOrderRepository standingOrderRepository, TransactionService transactionService,
            FinanceMapper financeMapper, UserRepository userRepository, WGRepository wgRepository) {
        this.standingOrderRepository = standingOrderRepository;
        this.transactionService = transactionService;
        this.objectMapper = new ObjectMapper();
        this.financeMapper = financeMapper;
        this.userRepository = userRepository;
        this.wgRepository = wgRepository;
    }

    /**
     * Create a new standing order
     */
    @Transactional
    public StandingOrder createStandingOrder(User creator, User creditor, WG wg, Double totalAmount, String description,
            StandingOrderFrequency frequency, LocalDate startDate, List<Long> debtorIds, List<Double> percentages,
            Integer monthlyDay, Boolean monthlyLastDay) {
        // Calculate next execution date
        LocalDate nextExecution;
        LocalDate now = LocalDate.now();

        if (frequency == StandingOrderFrequency.MONTHLY) {
            if (Boolean.TRUE.equals(monthlyLastDay)) {
                // Last day of month mode
                LocalDate lastDayThisMonth = now.withDayOfMonth(MonthlyScheduleUtil.getEffectiveLastDay(now));
                if (lastDayThisMonth.isAfter(now)) {
                    nextExecution = lastDayThisMonth;
                } else {
                    LocalDate nextMonth = now.plusMonths(1);
                    nextExecution = nextMonth.withDayOfMonth(MonthlyScheduleUtil.getEffectiveLastDay(nextMonth));
                }
            } else if (monthlyDay != null && monthlyDay >= 1 && monthlyDay <= 31) {
                // Fixed day mode
                int actualDay = MonthlyScheduleUtil.getEffectiveDay(now, monthlyDay);
                LocalDate candidateDate = now.withDayOfMonth(actualDay);
                if (candidateDate.isAfter(now)) {
                    nextExecution = candidateDate;
                } else {
                    // Next month
                    LocalDate nextMonth = now.plusMonths(1);
                    nextExecution = nextMonth.withDayOfMonth(MonthlyScheduleUtil.getEffectiveDay(nextMonth, monthlyDay));
                }
            } else {
                // Default: 1st of next month
                nextExecution = now.plusMonths(1).withDayOfMonth(1);
            }
        } else {
            // Weekly/Bi-weekly: user-selected date
            nextExecution = startDate;
        }

        // Build debtor data JSON
        String debtorData = buildDebtorDataJson(debtorIds, percentages);

        // Create order with monthly preferences (creator gets edit rights)
        StandingOrder order = new StandingOrder(creditor, creator, wg, totalAmount, description, frequency,
                nextExecution, debtorData, monthlyDay, monthlyLastDay);

        order = standingOrderRepository.save(order);

        // If the order is due today or earlier, execute it immediately
        // (handles case where user creates order after 12PM scheduler has run)
        if (!nextExecution.isAfter(LocalDate.now())) {
            try {
                executeStandingOrder(order);
                order.advanceNextExecution();
                standingOrderRepository.save(order);
                log.info("Standing order {} executed immediately (was due today)", order.getId());
            } catch (Exception e) {
                log.error("Failed to execute standing order immediately: {}", e.getMessage());
            }
        }

        return order;
    }

    /**
     * Process all due standing orders - runs at 12:00 PM daily
     */
    @Scheduled(cron = "0 0 12 * * ?")
    @Transactional
    public void processDueStandingOrdersScheduled() {
        processDueStandingOrders();
    }

    /**
     * Also process on application startup to catch any missed orders (e.g., if app
     * wasn't running for several days)
     */
    @EventListener(ApplicationReadyEvent.class)
    @Transactional
    public void processOnStartup() {
        log.info("Checking for due standing orders on startup...");
        processDueStandingOrders();
    }

    /**
     * Process all standing orders that are due
     */
    @Transactional
    public void processDueStandingOrders() {
        LocalDate today = LocalDate.now();
        // Use pessimistic lock to prevent double-execution when scheduler runs
        // concurrently
        List<StandingOrder> dueOrders = standingOrderRepository.findDueOrdersForUpdate(today);

        for (StandingOrder order : dueOrders) {
            try {
                executeStandingOrder(order);
                order.advanceNextExecution();
                standingOrderRepository.save(order);
            } catch (Exception e) {
                // Log error but continue with other orders
                log.error("Failed to execute standing order {}: {}", order.getId(), e.getMessage());
            }
        }
    }

    /**
     * Execute a single standing order by creating a transaction
     */
    @Transactional
    public void executeStandingOrder(StandingOrder order) {
        // Parse debtor data
        List<Long> debtorIds = new ArrayList<>();
        List<Double> percentages = new ArrayList<>();
        parseDebtorData(order.getDebtorData(), debtorIds, percentages);

        // Create the transaction (createdBy is whoever created the standing order)
        String description = order.getDescription() + " (Standing Order)";
        transactionService.createTransaction(order.getCreatedBy().getId(), // creator of the transaction
                order.getCreditor().getId(), // creditor (payer)
                debtorIds, percentages.isEmpty() ? null : percentages, order.getTotalAmount(), description);
    }

    /**
     * Deactivate a standing order
     */
    @Transactional
    public void deactivateStandingOrder(Long id) {
        StandingOrder order = standingOrderRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Standing order not found"));
        order.setIsActive(false);
        standingOrderRepository.save(order);
    }

    /**
     * Get all active standing orders for a WG
     */
    public List<StandingOrder> getActiveStandingOrders(WG wg) {
        return standingOrderRepository.findByWgAndIsActiveTrue(wg);
    }

    /**
     * Get a standing order by ID
     */
    public StandingOrder getStandingOrderById(Long id) {
        return standingOrderRepository.findById(id).orElseThrow(() -> new RuntimeException("Standing order not found"));
    }

    /**
     * Update an existing standing order Only the creditor (creator) can update a
     * standing order
     * 
     * @param id             The ID of the standing order to update
     * @param currentUserId  The ID of the user attempting the update
     * @param newCreditor    The new creditor (can be different)
     * @param totalAmount    Updated amount
     * @param description    Updated description
     * @param frequency      Updated frequency
     * @param debtorIds      Updated list of debtor IDs
     * @param percentages    Updated percentages (null for equal split)
     * @param monthlyDay     Updated monthly day (for monthly frequency)
     * @param monthlyLastDay Updated monthly last day flag
     * @return The updated standing order
     */
    @Transactional
    public StandingOrder updateStandingOrder(Long id, Long currentUserId, User newCreditor, Double totalAmount,
            String description, StandingOrderFrequency frequency, List<Long> debtorIds, List<Double> percentages,
            Integer monthlyDay, Boolean monthlyLastDay) {

        StandingOrder order = standingOrderRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Standing order not found"));

        // Only the original creator can edit the standing order
        if (!order.getCreatedBy().getId().equals(currentUserId)) {
            throw new RuntimeException("Only the creator of the standing order can edit it");
        }

        // Update basic fields
        order.setCreditor(newCreditor);
        order.setTotalAmount(totalAmount);
        order.setDescription(description);
        order.setFrequency(frequency);
        order.setMonthlyDay(monthlyDay);
        order.setMonthlyLastDay(monthlyLastDay != null ? monthlyLastDay : false);

        // Build new debtor data JSON
        String debtorData = buildDebtorDataJson(debtorIds, percentages);
        order.setDebtorData(debtorData);

        // Recalculate next execution if frequency changed
        LocalDate now = LocalDate.now();
        LocalDate currentNext = order.getNextExecution();

        // If next execution is in the past or today, recalculate based on new frequency
        if (!currentNext.isAfter(now)) {
            LocalDate newNextExecution;
            if (frequency == StandingOrderFrequency.MONTHLY) {
                if (Boolean.TRUE.equals(monthlyLastDay)) {
                    LocalDate nextMonth = now.plusMonths(1);
                    newNextExecution = nextMonth.withDayOfMonth(MonthlyScheduleUtil.getEffectiveLastDay(nextMonth));
                } else if (monthlyDay != null && monthlyDay >= 1 && monthlyDay <= 31) {
                    LocalDate nextMonth = now.plusMonths(1);
                    newNextExecution = nextMonth.withDayOfMonth(MonthlyScheduleUtil.getEffectiveDay(nextMonth, monthlyDay));
                } else {
                    newNextExecution = now.plusMonths(1).withDayOfMonth(1);
                }
            } else if (frequency == StandingOrderFrequency.WEEKLY) {
                newNextExecution = now.plusWeeks(1);
            } else { // BI_WEEKLY
                newNextExecution = now.plusWeeks(2);
            }
            order.setNextExecution(newNextExecution);
        }

        return standingOrderRepository.save(order);
    }

    private String buildDebtorDataJson(List<Long> debtorIds, List<Double> percentages) {
        List<Map<String, Object>> debtorList = new ArrayList<>();

        boolean hasPercentages = percentages != null && !percentages.isEmpty();
        double equalPercentage = hasPercentages ? 0 : 100.0 / debtorIds.size();

        for (int i = 0; i < debtorIds.size(); i++) {
            double pct = hasPercentages ? percentages.get(i) : equalPercentage;
            debtorList.add(Map.of("userId", debtorIds.get(i), "percentage", pct));
        }

        try {
            return objectMapper.writeValueAsString(debtorList);
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Failed to serialize debtor data", e);
        }
    }

    private void parseDebtorData(String json, List<Long> debtorIds, List<Double> percentages) {
        if (json == null || json.isEmpty()) {
            return;
        }

        try {
            List<Map<String, Object>> debtorList = objectMapper.readValue(json,
                    new TypeReference<List<Map<String, Object>>>() {
                    });

            for (Map<String, Object> entry : debtorList) {
                Object userIdObj = entry.get("userId");
                Object percentageObj = entry.get("percentage");

                Long userId = userIdObj instanceof Number ? ((Number) userIdObj).longValue()
                        : Long.parseLong(userIdObj.toString());
                Double percentage = percentageObj instanceof Number ? ((Number) percentageObj).doubleValue()
                        : Double.parseDouble(percentageObj.toString());

                debtorIds.add(userId);
                percentages.add(percentage);
            }
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Failed to parse debtor data", e);
        }
    }

    // ==================== DTO METHODS ====================
    // These methods return DTOs instead of entities for UI consumption

    /**
     * Get all active standing orders for a WG as DTOs
     */
    public List<StandingOrderDTO> getActiveStandingOrdersDTO(WG wg) {
        List<StandingOrder> orders = getActiveStandingOrders(wg);
        return financeMapper.toStandingOrderDTOList(orders);
    }

    public List<StandingOrderViewDTO> getActiveStandingOrdersView(WG wg) {
        List<StandingOrder> orders = getActiveStandingOrders(wg);
        return financeMapper.toStandingOrderViewList(orders);
    }

    /**
     * Get all active standing orders for a WG by ID as DTOs.
     */
    public List<StandingOrderDTO> getActiveStandingOrdersDTO(Long wgId) {
        if (wgId == null) {
            return List.of();
        }
        WG wg = wgRepository.findById(wgId).orElse(null);
        if (wg == null) {
            return List.of();
        }
        return getActiveStandingOrdersDTO(wg);
    }

    public List<StandingOrderViewDTO> getActiveStandingOrdersView(Long wgId) {
        if (wgId == null) {
            return List.of();
        }
        WG wg = wgRepository.findById(wgId).orElse(null);
        if (wg == null) {
            return List.of();
        }
        return getActiveStandingOrdersView(wg);
    }

    /**
     * Get a standing order by ID as DTO
     */
    public StandingOrderDTO getStandingOrderByIdDTO(Long id) {
        StandingOrder order = getStandingOrderById(id);
        return financeMapper.toDTO(order);
    }

    /**
     * Get a standing order by ID as view DTO
     */
    public StandingOrderViewDTO getStandingOrderByIdView(Long id) {
        StandingOrder order = getStandingOrderById(id);
        return financeMapper.toStandingOrderView(order);
    }

    /**
     * Create a standing order and return as DTO
     */
    @Transactional
    public StandingOrderDTO createStandingOrderDTO(User creator, User creditor, WG wg, Double totalAmount,
            String description, StandingOrderFrequency frequency, LocalDate startDate, List<Long> debtorIds,
            List<Double> percentages, Integer monthlyDay, Boolean monthlyLastDay) {
        StandingOrder order = createStandingOrder(creator, creditor, wg, totalAmount, description, frequency, startDate,
                debtorIds, percentages, monthlyDay, monthlyLastDay);
        return financeMapper.toDTO(order);
    }

    /**
     * Create a standing order (using IDs) and return as DTO
     */
    @Transactional
    public StandingOrderDTO createStandingOrderDTO(Long creatorId, Long creditorId, Long wgId, Double totalAmount,
            String description, StandingOrderFrequency frequency, LocalDate startDate, List<Long> debtorIds,
            List<Double> percentages, Integer monthlyDay, Boolean monthlyLastDay) {

        User creator = userRepository.findById(creatorId)
                .orElseThrow(() -> new IllegalArgumentException("Creator not found"));
        User creditor = userRepository.findById(creditorId)
                .orElseThrow(() -> new IllegalArgumentException("Creditor not found"));
        WG wg = null;
        if (wgId != null) {
            wg = wgRepository.findById(wgId).orElse(null);
        }
        if (wg == null) {
            wg = creator.getWg();
        }
        if (wg == null) {
            throw new IllegalArgumentException("WG not found for standing order creation");
        }

        StandingOrder order = createStandingOrder(creator, creditor, wg, totalAmount, description, frequency, startDate,
                debtorIds, percentages, monthlyDay, monthlyLastDay);
        return financeMapper.toDTO(order);
    }

    /**
     * Create a standing order (using IDs) and return as view DTO
     */
    @Transactional
    public StandingOrderViewDTO createStandingOrderView(Long creatorId, Long creditorId, Long wgId, Double totalAmount,
            String description, StandingOrderFrequency frequency, LocalDate startDate, List<Long> debtorIds,
            List<Double> percentages, Integer monthlyDay, Boolean monthlyLastDay) {

        StandingOrderDTO dto = createStandingOrderDTO(creatorId, creditorId, wgId, totalAmount, description, frequency,
                startDate, debtorIds, percentages, monthlyDay, monthlyLastDay);
        return getStandingOrderByIdView(dto.id());
    }

    /**
     * Update a standing order and return as DTO
     */
    @Transactional
    public StandingOrderDTO updateStandingOrderDTO(Long id, Long currentUserId, User newCreditor, Double totalAmount,
            String description, StandingOrderFrequency frequency, List<Long> debtorIds, List<Double> percentages,
            Integer monthlyDay, Boolean monthlyLastDay) {
        StandingOrder order = updateStandingOrder(id, currentUserId, newCreditor, totalAmount, description, frequency,
                debtorIds, percentages, monthlyDay, monthlyLastDay);
        return financeMapper.toDTO(order);
    }

    /**
     * Update a standing order (using IDs) and return as DTO
     */
    @Transactional
    public StandingOrderDTO updateStandingOrderDTO(Long id, Long currentUserId, Long newCreditorId, Double totalAmount,
            String description, StandingOrderFrequency frequency, List<Long> debtorIds, List<Double> percentages,
            Integer monthlyDay, Boolean monthlyLastDay) {

        User newCreditor = userRepository.findById(newCreditorId)
                .orElseThrow(() -> new IllegalArgumentException("Creditor not found"));

        StandingOrder order = updateStandingOrder(id, currentUserId, newCreditor, totalAmount, description, frequency,
                debtorIds, percentages, monthlyDay, monthlyLastDay);
        return financeMapper.toDTO(order);
    }

    /**
     * Update a standing order (using IDs) and return as view DTO
     */
    @Transactional
    public StandingOrderViewDTO updateStandingOrderView(Long id, Long currentUserId, Long newCreditorId,
            Double totalAmount, String description, StandingOrderFrequency frequency, List<Long> debtorIds,
            List<Double> percentages, Integer monthlyDay, Boolean monthlyLastDay) {

        StandingOrderDTO dto = updateStandingOrderDTO(id, currentUserId, newCreditorId, totalAmount, description,
                frequency, debtorIds, percentages, monthlyDay, monthlyLastDay);
        return getStandingOrderByIdView(dto.id());
    }
}

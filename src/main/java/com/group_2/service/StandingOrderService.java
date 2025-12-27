package com.group_2.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.group_2.repository.StandingOrderRepository;
import com.model.StandingOrder;
import com.model.StandingOrderFrequency;
import com.model.User;
import com.model.WG;
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

    private final StandingOrderRepository standingOrderRepository;
    private final TransactionService transactionService;
    private final ObjectMapper objectMapper;

    @Autowired
    public StandingOrderService(StandingOrderRepository standingOrderRepository,
            TransactionService transactionService) {
        this.standingOrderRepository = standingOrderRepository;
        this.transactionService = transactionService;
        this.objectMapper = new ObjectMapper();
    }

    /**
     * Create a new standing order
     */
    @Transactional
    public StandingOrder createStandingOrder(User creator, User creditor, WG wg, Double totalAmount,
            String description, StandingOrderFrequency frequency,
            LocalDate startDate, List<Long> debtorIds, List<Double> percentages,
            Integer monthlyDay, Boolean monthlyLastDay) {
        // Calculate next execution date
        LocalDate nextExecution;
        LocalDate now = LocalDate.now();

        if (frequency == StandingOrderFrequency.MONTHLY) {
            if (Boolean.TRUE.equals(monthlyLastDay)) {
                // Last day of month mode
                LocalDate lastDayThisMonth = now.withDayOfMonth(now.lengthOfMonth());
                if (lastDayThisMonth.isAfter(now)) {
                    nextExecution = lastDayThisMonth;
                } else {
                    nextExecution = now.plusMonths(1).withDayOfMonth(now.plusMonths(1).lengthOfMonth());
                }
            } else if (monthlyDay != null && monthlyDay >= 1 && monthlyDay <= 31) {
                // Fixed day mode
                int daysThisMonth = now.lengthOfMonth();
                int actualDay = Math.min(monthlyDay, daysThisMonth);
                LocalDate candidateDate = now.withDayOfMonth(actualDay);
                if (candidateDate.isAfter(now)) {
                    nextExecution = candidateDate;
                } else {
                    // Next month
                    LocalDate nextMonth = now.plusMonths(1);
                    int daysNextMonth = nextMonth.lengthOfMonth();
                    nextExecution = nextMonth.withDayOfMonth(Math.min(monthlyDay, daysNextMonth));
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
        StandingOrder order = new StandingOrder(creditor, creator, wg, totalAmount, description,
                frequency, nextExecution, debtorData, monthlyDay, monthlyLastDay);

        order = standingOrderRepository.save(order);

        // If the order is due today or earlier, execute it immediately
        // (handles case where user creates order after 12PM scheduler has run)
        if (!nextExecution.isAfter(LocalDate.now())) {
            try {
                executeStandingOrder(order);
                order.advanceNextExecution();
                standingOrderRepository.save(order);
                System.out.println("Standing order " + order.getId() + " executed immediately (was due today)");
            } catch (Exception e) {
                System.err.println("Failed to execute standing order immediately: " + e.getMessage());
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
     * Also process on application startup to catch any missed orders
     * (e.g., if app wasn't running for several days)
     */
    @EventListener(ApplicationReadyEvent.class)
    @Transactional
    public void processOnStartup() {
        System.out.println("Checking for due standing orders on startup...");
        processDueStandingOrders();
    }

    /**
     * Process all standing orders that are due
     */
    @Transactional
    public void processDueStandingOrders() {
        LocalDate today = LocalDate.now();
        List<StandingOrder> dueOrders = standingOrderRepository
                .findByNextExecutionLessThanEqualAndIsActiveTrue(today);

        for (StandingOrder order : dueOrders) {
            try {
                executeStandingOrder(order);
                order.advanceNextExecution();
                standingOrderRepository.save(order);
            } catch (Exception e) {
                // Log error but continue with other orders
                System.err.println("Failed to execute standing order " + order.getId() + ": " + e.getMessage());
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
        transactionService.createTransaction(
                order.getCreatedBy().getId(), // creator of the transaction
                order.getCreditor().getId(), // creditor (payer)
                debtorIds,
                percentages.isEmpty() ? null : percentages,
                order.getTotalAmount(),
                description);
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
        return standingOrderRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Standing order not found"));
    }

    /**
     * Update an existing standing order
     * Only the creditor (creator) can update a standing order
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
    public StandingOrder updateStandingOrder(Long id, Long currentUserId, User newCreditor,
            Double totalAmount, String description, StandingOrderFrequency frequency,
            List<Long> debtorIds, List<Double> percentages,
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
                    newNextExecution = now.plusMonths(1).withDayOfMonth(now.plusMonths(1).lengthOfMonth());
                } else if (monthlyDay != null && monthlyDay >= 1 && monthlyDay <= 31) {
                    LocalDate nextMonth = now.plusMonths(1);
                    int daysNextMonth = nextMonth.lengthOfMonth();
                    newNextExecution = nextMonth.withDayOfMonth(Math.min(monthlyDay, daysNextMonth));
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
}

package com.group_2.dto.finance;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.group_2.dto.core.CoreMapper;
import com.group_2.dto.core.UserSummaryDTO;
import com.group_2.dto.finance.TransactionSplitViewDTO;
import com.group_2.dto.finance.TransactionViewDTO;
import com.group_2.dto.finance.BalanceViewDTO;
import com.group_2.dto.finance.StandingOrderViewDTO;
import com.group_2.model.User;
import com.group_2.model.finance.StandingOrder;
import com.group_2.model.finance.Transaction;
import com.group_2.model.finance.TransactionSplit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

/**
 * Mapper for converting finance entities to DTOs. Centralizes the mapping logic
 * and user name resolution.
 * 
 * Note: This mapper does NOT access repositories directly. User resolution
 * should be handled by the calling service layer.
 */
@Component
public class FinanceMapper {

    private static final Logger log = LoggerFactory.getLogger(FinanceMapper.class);
    private final ObjectMapper objectMapper;
    private final CoreMapper coreMapper;

    public FinanceMapper(CoreMapper coreMapper) {
        this.coreMapper = coreMapper;
        this.objectMapper = new ObjectMapper();
    }

    /**
     * Convert a Transaction entity to DTO
     */
    public TransactionDTO toDTO(Transaction transaction) {
        if (transaction == null)
            return null;

        List<TransactionSplitDTO> splitDTOs = new ArrayList<>();
        if (transaction.getSplits() != null) {
            for (TransactionSplit split : transaction.getSplits()) {
                splitDTOs.add(toDTO(split));
            }
        }

        return new TransactionDTO(transaction.getId(), transaction.getCreditor().getId(),
                getDisplayName(transaction.getCreditor()), transaction.getCreatedBy().getId(),
                getDisplayName(transaction.getCreatedBy()), transaction.getTotalAmount(), transaction.getDescription(),
                transaction.getTimestamp(), transaction.getWg().getId(), splitDTOs);
    }

    /**
     * Convert a TransactionSplit entity to DTO
     */
    public TransactionSplitDTO toDTO(TransactionSplit split) {
        if (split == null)
            return null;

        return new TransactionSplitDTO(split.getId(), split.getDebtor().getId(), getDisplayName(split.getDebtor()),
                split.getPercentage(), split.getAmount());
    }

    /**
     * Convert a StandingOrder entity to DTO.
     * Requires a user resolver function to look up user names by ID.
     */
    public StandingOrderDTO toDTO(StandingOrder order, Function<Long, User> userResolver) {
        if (order == null)
            return null;

        List<StandingOrderDTO.DebtorShareDTO> debtorDTOs = parseDebtorData(order.getDebtorData(),
                order.getTotalAmount(), userResolver);

        return new StandingOrderDTO(order.getId(), order.getCreditor().getId(), getDisplayName(order.getCreditor()),
                order.getCreatedBy().getId(), getDisplayName(order.getCreatedBy()), order.getTotalAmount(),
                order.getDescription(), order.getFrequency(), order.getNextExecution(), order.getIsActive(),
                order.getCreatedAt(), order.getMonthlyDay(), order.getMonthlyLastDay(), debtorDTOs);
    }

    /**
     * Convert a StandingOrder entity to DTO (uses entity users directly).
     */
    public StandingOrderDTO toDTO(StandingOrder order) {
        return toDTO(order, id -> null); // No external resolution needed if debtors are embedded
    }

    /**
     * Create a BalanceDTO from user entity and balance
     */
    public BalanceDTO toBalanceDTO(User user, Double balance) {
        if (user == null)
            return null;

        return new BalanceDTO(user.getId(), getDisplayName(user), balance);
    }

    /**
     * Convert a list of transactions to DTOs
     */
    public List<TransactionDTO> toDTOList(List<Transaction> transactions) {
        List<TransactionDTO> dtos = new ArrayList<>();
        if (transactions != null) {
            for (Transaction transaction : transactions) {
                dtos.add(toDTO(transaction));
            }
        }
        return dtos;
    }

    /**
     * Convert a list of standing orders to DTOs
     */
    public List<StandingOrderDTO> toStandingOrderDTOList(List<StandingOrder> orders,
            Function<Long, User> userResolver) {
        List<StandingOrderDTO> dtos = new ArrayList<>();
        if (orders != null) {
            for (StandingOrder order : orders) {
                dtos.add(toDTO(order, userResolver));
            }
        }
        return dtos;
    }

    /**
     * Convert a list of standing orders to DTOs (no external resolution)
     */
    public List<StandingOrderDTO> toStandingOrderDTOList(List<StandingOrder> orders) {
        return toStandingOrderDTOList(orders, id -> null);
    }

    /**
     * Get display name for a user (first name + optional surname)
     */
    private String getDisplayName(User user) {
        if (user == null)
            return "Unknown";
        String name = user.getName();
        if (user.getSurname() != null && !user.getSurname().isEmpty()) {
            name += " " + user.getSurname();
        }
        return name;
    }

    /**
     * Parse the JSON debtor data string from StandingOrder.
     * Returns parsed data with percentage and amount calculations.
     * User resolution is delegated to the provided function.
     */
    private List<StandingOrderDTO.DebtorShareDTO> parseDebtorData(String json, Double totalAmount,
            Function<Long, User> userResolver) {
        List<StandingOrderDTO.DebtorShareDTO> debtors = new ArrayList<>();

        if (json == null || json.isEmpty()) {
            return debtors;
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

                Double amount = (percentage / 100.0) * totalAmount;

                // Resolve user name via provided function
                String userName = "Unknown User";
                if (userResolver != null) {
                    User user = userResolver.apply(userId);
                    if (user != null) {
                        userName = getDisplayName(user);
                    }
                }

                debtors.add(new StandingOrderDTO.DebtorShareDTO(userId, userName, percentage, amount));
            }
        } catch (Exception e) {
            log.error("Failed to parse debtor data JSON: {}", e.getMessage(), e);
        }

        return debtors;
    }

    // ===== View-facing DTOs (nested summaries) =====

    public TransactionViewDTO toView(Transaction transaction) {
        if (transaction == null) {
            return null;
        }
        List<TransactionSplitViewDTO> splitViews = new ArrayList<>();
        if (transaction.getSplits() != null) {
            for (TransactionSplit split : transaction.getSplits()) {
                splitViews.add(toView(split));
            }
        }
        return new TransactionViewDTO(transaction.getId(), coreMapper.toUserSummary(transaction.getCreditor()),
                coreMapper.toUserSummary(transaction.getCreatedBy()), transaction.getTotalAmount(),
                transaction.getDescription(), transaction.getTimestamp(), coreMapper.toWgSummary(transaction.getWg()),
                splitViews);
    }

    public TransactionSplitViewDTO toView(TransactionSplit split) {
        if (split == null) {
            return null;
        }
        return new TransactionSplitViewDTO(split.getId(), coreMapper.toUserSummary(split.getDebtor()),
                split.getPercentage(), split.getAmount());
    }

    public List<TransactionViewDTO> toViewList(List<Transaction> transactions) {
        List<TransactionViewDTO> dtos = new ArrayList<>();
        if (transactions != null) {
            for (Transaction transaction : transactions) {
                dtos.add(toView(transaction));
            }
        }
        return dtos;
    }

    public BalanceViewDTO toBalanceView(User user, Double balance) {
        if (user == null) {
            return null;
        }
        UserSummaryDTO summary = coreMapper.toUserSummary(user);
        return new BalanceViewDTO(summary, balance);
    }

    public StandingOrderViewDTO toStandingOrderView(StandingOrder order, Function<Long, User> userResolver) {
        if (order == null) {
            return null;
        }
        List<StandingOrderViewDTO.DebtorShareViewDTO> debtorDTOs = new ArrayList<>();
        List<StandingOrderDTO.DebtorShareDTO> parsed = parseDebtorData(order.getDebtorData(), order.getTotalAmount(),
                userResolver);
        for (StandingOrderDTO.DebtorShareDTO d : parsed) {
            User debtor = userResolver != null ? userResolver.apply(d.userId()) : null;
            debtorDTOs.add(new StandingOrderViewDTO.DebtorShareViewDTO(d.userId(), coreMapper.toUserSummary(debtor),
                    d.percentage(), d.amount()));
        }
        return new StandingOrderViewDTO(order.getId(), coreMapper.toUserSummary(order.getCreditor()),
                coreMapper.toUserSummary(order.getCreatedBy()), order.getTotalAmount(), order.getDescription(),
                order.getFrequency(), order.getNextExecution(), order.getIsActive(), order.getCreatedAt(),
                order.getMonthlyDay(), order.getMonthlyLastDay(), debtorDTOs, coreMapper.toWgSummary(order.getWg()));
    }

    public StandingOrderViewDTO toStandingOrderView(StandingOrder order) {
        return toStandingOrderView(order, id -> null);
    }

    public List<StandingOrderViewDTO> toStandingOrderViewList(List<StandingOrder> orders,
            Function<Long, User> userResolver) {
        List<StandingOrderViewDTO> dtos = new ArrayList<>();
        if (orders != null) {
            for (StandingOrder order : orders) {
                dtos.add(toStandingOrderView(order, userResolver));
            }
        }
        return dtos;
    }

    public List<StandingOrderViewDTO> toStandingOrderViewList(List<StandingOrder> orders) {
        return toStandingOrderViewList(orders, id -> null);
    }
}

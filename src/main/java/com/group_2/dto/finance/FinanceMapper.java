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
import com.group_2.repository.UserRepository;

import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Mapper for converting finance entities to DTOs. Centralizes the mapping logic
 * and user name resolution.
 */
@Component
public class FinanceMapper {

    private final UserRepository userRepository;
    private final ObjectMapper objectMapper;
    private final CoreMapper coreMapper;

    public FinanceMapper(UserRepository userRepository, CoreMapper coreMapper) {
        this.userRepository = userRepository;
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
     * Convert a StandingOrder entity to DTO
     */
    public StandingOrderDTO toDTO(StandingOrder order) {
        if (order == null)
            return null;

        List<StandingOrderDTO.DebtorShareDTO> debtorDTOs = parseDebtorData(order.getDebtorData(),
                order.getTotalAmount());

        return new StandingOrderDTO(order.getId(), order.getCreditor().getId(), getDisplayName(order.getCreditor()),
                order.getCreatedBy().getId(), getDisplayName(order.getCreatedBy()), order.getTotalAmount(),
                order.getDescription(), order.getFrequency(), order.getNextExecution(), order.getIsActive(),
                order.getCreatedAt(), order.getMonthlyDay(), order.getMonthlyLastDay(), debtorDTOs);
    }

    /**
     * Create a BalanceDTO
     */
    public BalanceDTO toBalanceDTO(User user, Double balance) {
        if (user == null)
            return null;

        return new BalanceDTO(user.getId(), getDisplayName(user), balance);
    }

    /**
     * Create a BalanceDTO from user ID and balance
     */
    public BalanceDTO toBalanceDTO(Long userId, Double balance) {
        User user = userRepository.findById(userId).orElse(null);
        if (user == null)
            return null;

        return toBalanceDTO(user, balance);
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
    public List<StandingOrderDTO> toStandingOrderDTOList(List<StandingOrder> orders) {
        List<StandingOrderDTO> dtos = new ArrayList<>();
        if (orders != null) {
            for (StandingOrder order : orders) {
                dtos.add(toDTO(order));
            }
        }
        return dtos;
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
     * Parse the JSON debtor data string from StandingOrder
     */
    private List<StandingOrderDTO.DebtorShareDTO> parseDebtorData(String json, Double totalAmount) {
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

                // Resolve user name
                String userName = userRepository.findById(userId).map(this::getDisplayName).orElse("Unknown User");

                debtors.add(new StandingOrderDTO.DebtorShareDTO(userId, userName, percentage, amount));
            }
        } catch (Exception e) {
            // Log and return empty list on parse failure
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

    public StandingOrderViewDTO toStandingOrderView(StandingOrder order) {
        if (order == null) {
            return null;
        }
        List<StandingOrderViewDTO.DebtorShareViewDTO> debtorDTOs = new ArrayList<>();
        List<StandingOrderDTO.DebtorShareDTO> parsed = parseDebtorData(order.getDebtorData(), order.getTotalAmount());
        for (StandingOrderDTO.DebtorShareDTO d : parsed) {
            User debtor = userRepository.findById(d.userId()).orElse(null);
            debtorDTOs.add(new StandingOrderViewDTO.DebtorShareViewDTO(d.userId(), coreMapper.toUserSummary(debtor),
                    d.percentage(), d.amount()));
        }
        return new StandingOrderViewDTO(order.getId(), coreMapper.toUserSummary(order.getCreditor()),
                coreMapper.toUserSummary(order.getCreatedBy()), order.getTotalAmount(), order.getDescription(),
                order.getFrequency(), order.getNextExecution(), order.getIsActive(), order.getCreatedAt(),
                order.getMonthlyDay(), order.getMonthlyLastDay(), debtorDTOs, coreMapper.toWgSummary(order.getWg()));
    }

    public List<StandingOrderViewDTO> toStandingOrderViewList(List<StandingOrder> orders) {
        List<StandingOrderViewDTO> dtos = new ArrayList<>();
        if (orders != null) {
            for (StandingOrder order : orders) {
                dtos.add(toStandingOrderView(order));
            }
        }
        return dtos;
    }
}

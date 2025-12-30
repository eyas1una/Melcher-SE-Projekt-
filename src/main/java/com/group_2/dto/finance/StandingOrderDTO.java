package com.group_2.dto.finance;

import com.group_2.model.finance.StandingOrderFrequency;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * DTO representing a standing order for display in the UI. Immutable record
 * containing only the display data needed by controllers.
 */
public record StandingOrderDTO(Long id, Long creditorId, String creditorName, Long createdById, String createdByName,
        Double totalAmount, String description, StandingOrderFrequency frequency, LocalDate nextExecution,
        Boolean isActive, LocalDateTime createdAt, Integer monthlyDay, Boolean monthlyLastDay,
        List<DebtorShareDTO> debtors) {
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("dd.MM.yyyy");

    /**
     * DTO for debtor share information in a standing order
     */
    public record DebtorShareDTO(Long userId, String userName, Double percentage, Double amount) {
        public String getFormattedAmount() {
            return String.format("%.2f€", amount);
        }

        public String getFormattedPercentage() {
            return String.format("%.1f%%", percentage);
        }
    }

    /**
     * Create a display-friendly amount string with Euro symbol
     */
    public String getFormattedAmount() {
        return String.format("%.2f€", totalAmount);
    }

    /**
     * Get formatted next execution date
     */
    public String getFormattedNextExecution() {
        return nextExecution.format(DATE_FORMATTER);
    }

    /**
     * Get a human-readable frequency description
     */
    public String getFrequencyDescription() {
        if (frequency == null)
            return "Unknown";

        switch (frequency) {
        case WEEKLY:
            return "Weekly";
        case BI_WEEKLY:
            return "Every 2 weeks";
        case MONTHLY:
            if (Boolean.TRUE.equals(monthlyLastDay)) {
                return "Monthly (last day)";
            } else if (monthlyDay != null) {
                return "Monthly (day " + monthlyDay + ")";
            }
            return "Monthly";
        default:
            return frequency.toString();
        }
    }

    /**
     * Check if the given user is the creator (has edit rights)
     */
    public boolean canBeEditedBy(Long userId) {
        return createdById != null && createdById.equals(userId);
    }

    /**
     * Get a summary of debtors for display
     */
    public String getDebtorsSummary() {
        if (debtors == null || debtors.isEmpty()) {
            return "No debtors";
        }
        if (debtors.size() == 1) {
            return debtors.get(0).userName();
        }
        return debtors.size() + " people";
    }

    /**
     * Get status text for display
     */
    public String getStatusText() {
        return Boolean.TRUE.equals(isActive) ? "Active" : "Inactive";
    }
}

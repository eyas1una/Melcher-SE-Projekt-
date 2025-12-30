package com.group_2.dto.finance;

/**
 * DTO representing a transaction split (debtor's portion of a transaction).
 * Immutable record containing only the display data needed by controllers.
 */
public record TransactionSplitDTO(Long id, Long debtorId, String debtorName, Double percentage, Double amount) {
    /**
     * Create a display-friendly amount string with Euro symbol
     */
    public String getFormattedAmount() {
        return String.format("%.2f€", amount);
    }

    /**
     * Create a display-friendly percentage string
     */
    public String getFormattedPercentage() {
        return String.format("%.1f%%", percentage);
    }
}

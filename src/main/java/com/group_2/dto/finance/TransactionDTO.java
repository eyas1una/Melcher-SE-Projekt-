package com.group_2.dto.finance;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * DTO representing a transaction for display in the UI. Immutable record
 * containing only the display data needed by controllers.
 */
public record TransactionDTO(Long id, Long creditorId, String creditorName, Long createdById, String createdByName,
        Double totalAmount, String description, LocalDateTime timestamp, Long wgId, List<TransactionSplitDTO> splits) {
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("dd.MM.yyyy");
    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("HH:mm");
    private static final DateTimeFormatter FULL_FORMATTER = DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm");

    /**
     * Create a display-friendly amount string with Euro symbol
     */
    public String getFormattedAmount() {
        return String.format("%.2f€", totalAmount);
    }

    /**
     * Get formatted date only
     */
    public String getFormattedDate() {
        return timestamp.format(DATE_FORMATTER);
    }

    /**
     * Get formatted time only
     */
    public String getFormattedTime() {
        return timestamp.format(TIME_FORMATTER);
    }

    /**
     * Get full formatted date and time
     */
    public String getFormattedDateTime() {
        return timestamp.format(FULL_FORMATTER);
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
        if (splits == null || splits.isEmpty()) {
            return "No debtors";
        }
        if (splits.size() == 1) {
            return splits.get(0).debtorName();
        }
        return splits.size() + " people";
    }
}

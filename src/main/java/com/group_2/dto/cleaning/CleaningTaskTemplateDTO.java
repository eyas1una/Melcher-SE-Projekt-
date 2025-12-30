package com.group_2.dto.cleaning;

import com.group_2.model.cleaning.RecurrenceInterval;

/**
 * Immutable DTO for cleaning task templates to decouple UI from JPA entities.
 */
public record CleaningTaskTemplateDTO(Long id, Long roomId, String roomName, int dayOfWeek,
        RecurrenceInterval recurrenceInterval) {

    /**
     * Get display-friendly day name
     */
    public String getDayName() {
        return switch (dayOfWeek) {
        case 1 -> "Monday";
        case 2 -> "Tuesday";
        case 3 -> "Wednesday";
        case 4 -> "Thursday";
        case 5 -> "Friday";
        case 6 -> "Saturday";
        case 7 -> "Sunday";
        default -> "Unknown";
        };
    }
}

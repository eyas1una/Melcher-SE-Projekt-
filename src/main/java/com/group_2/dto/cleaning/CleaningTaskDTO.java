package com.group_2.dto.cleaning;

import java.time.LocalDate;

/**
 * Immutable DTO for cleaning tasks to decouple UI from JPA entities.
 */
public record CleaningTaskDTO(
        Long id,
        Long roomId,
        String roomName,
        Long assigneeId,
        String assigneeName,
        LocalDate weekStartDate,
        LocalDate dueDate,
        boolean completed,
        boolean manualOverride) {
}

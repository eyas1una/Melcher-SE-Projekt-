package com.group_2.dto.core;

/**
 * Lightweight representation of a user for UI/view-model consumption.
 */
public record UserSummaryDTO(Long id, String name, String surname, String email, Long wgId) {
    public String displayName() {
        if (surname == null || surname.isBlank()) {
            return name;
        }
        return name + " " + surname;
    }
}

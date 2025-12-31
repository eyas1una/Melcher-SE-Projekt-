package com.group_2.dto.core;

/**
 * Immutable session snapshot for UI consumption.
 */
public record UserSessionDTO(Long userId, String name, String surname, Long wgId) {
    public String displayName() {
        if (surname == null || surname.isBlank()) {
            return name;
        }
        return name + " " + surname;
    }
}

package com.group_2.dto.core;

/**
 * View-model for profile screens. Keeps UI layers off entities.
 */
public record UserProfileViewDTO(UserSummaryDTO user, WgSummaryDTO wg, boolean admin) {
    public String displayName() {
        return user != null ? user.displayName() : "Unknown";
    }
}

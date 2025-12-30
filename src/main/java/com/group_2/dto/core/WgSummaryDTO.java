package com.group_2.dto.core;

/**
 * Lightweight representation of a household (WG) for UI/view-model consumption.
 */
public record WgSummaryDTO(Long id, String name, int memberCount) {
}

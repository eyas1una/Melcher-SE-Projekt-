package com.group_2.dto.finance;

import com.group_2.dto.core.UserSummaryDTO;

/**
 * View-facing balance DTO with nested user summary.
 */
public record BalanceViewDTO(UserSummaryDTO user, Double balance) {
}

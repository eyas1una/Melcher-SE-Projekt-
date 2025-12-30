package com.group_2.dto.finance;

import com.group_2.dto.core.UserSummaryDTO;

/**
 * View-facing split DTO with nested debtor summary.
 */
public record TransactionSplitViewDTO(Long id, UserSummaryDTO debtor, Double percentage, Double amount) {
}

package com.group_2.dto.finance;

import com.group_2.dto.core.UserSummaryDTO;
import com.group_2.dto.core.WgSummaryDTO;

import java.time.LocalDateTime;
import java.util.List;

/**
 * View-facing transaction DTO with nested user summaries.
 */
public record TransactionViewDTO(Long id, UserSummaryDTO creditor, UserSummaryDTO createdBy, Double totalAmount,
        String description, LocalDateTime timestamp, WgSummaryDTO wg, List<TransactionSplitViewDTO> splits) {
}

package com.group_2.dto.finance;

import com.group_2.dto.core.UserSummaryDTO;
import com.group_2.dto.core.WgSummaryDTO;
import com.group_2.model.finance.StandingOrderFrequency;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * View-facing standing order DTO with nested user summaries.
 */
public record StandingOrderViewDTO(Long id, UserSummaryDTO creditor, UserSummaryDTO createdBy, Double totalAmount,
        String description, StandingOrderFrequency frequency, LocalDate nextExecution, Boolean isActive,
        LocalDateTime createdAt, Integer monthlyDay, Boolean monthlyLastDay, List<StandingOrderViewDTO.DebtorShareViewDTO> debtors,
        WgSummaryDTO wg) {

    public record DebtorShareViewDTO(Long userId, UserSummaryDTO user, Double percentage, Double amount) {
    }
}

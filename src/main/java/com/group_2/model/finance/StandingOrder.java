package com.group_2.model.finance;

import com.group_2.model.User;
import com.group_2.model.WG;
import jakarta.persistence.*;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Entity representing a standing order (recurring transaction).
 * Stores the template for transactions that will be created automatically.
 */
@Entity
@Table(name = "standing_orders", indexes = {
        @Index(name = "idx_standing_order_wg", columnList = "wg_id"),
        @Index(name = "idx_standing_order_next_exec", columnList = "next_execution")
})
public class StandingOrder {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "creditor_id", nullable = false)
    private User creditor;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "created_by_id", nullable = false)
    private User createdBy;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "wg_id", nullable = false)
    private WG wg;

    @Column(nullable = false)
    private Double totalAmount;

    @Column(length = 500)
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private StandingOrderFrequency frequency;

    @Column(nullable = false)
    private LocalDate nextExecution;

    @Column(nullable = false)
    private Boolean isActive = true;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    /**
     * JSON string storing debtor IDs and their percentages.
     * Format: [{"userId": 1, "percentage": 50.0}, {"userId": 2, "percentage":
     * 50.0}]
     */
    @Column(length = 2000)
    private String debtorData;

    /**
     * For MONTHLY frequency: the preferred day of month (1-31).
     * If the day doesn't exist in a month, execution falls back to last day of
     * month.
     */
    @Column
    private Integer monthlyDay;

    /**
     * For MONTHLY frequency: if true, always execute on last day of month.
     */
    @Column
    private Boolean monthlyLastDay = false;

    public StandingOrder() {
        this.createdAt = LocalDateTime.now();
        this.isActive = true;
    }

    public StandingOrder(User creditor, User createdBy, WG wg, Double totalAmount, String description,
            StandingOrderFrequency frequency, LocalDate nextExecution, String debtorData) {
        this.creditor = creditor;
        this.createdBy = createdBy;
        this.wg = wg;
        this.totalAmount = totalAmount;
        this.description = description;
        this.frequency = frequency;
        this.nextExecution = nextExecution;
        this.debtorData = debtorData;
        this.createdAt = LocalDateTime.now();
        this.isActive = true;
        this.monthlyLastDay = false;
    }

    public StandingOrder(User creditor, User createdBy, WG wg, Double totalAmount, String description,
            StandingOrderFrequency frequency, LocalDate nextExecution, String debtorData,
            Integer monthlyDay, Boolean monthlyLastDay) {
        this.creditor = creditor;
        this.createdBy = createdBy;
        this.wg = wg;
        this.totalAmount = totalAmount;
        this.description = description;
        this.frequency = frequency;
        this.nextExecution = nextExecution;
        this.debtorData = debtorData;
        this.monthlyDay = monthlyDay;
        this.monthlyLastDay = monthlyLastDay != null ? monthlyLastDay : false;
        this.createdAt = LocalDateTime.now();
        this.isActive = true;
    }

    // Getters and Setters
    public Long getId() {
        return id;
    }

    public User getCreditor() {
        return creditor;
    }

    public void setCreditor(User creditor) {
        this.creditor = creditor;
    }

    public User getCreatedBy() {
        return createdBy;
    }

    public void setCreatedBy(User createdBy) {
        this.createdBy = createdBy;
    }

    public WG getWg() {
        return wg;
    }

    public void setWg(WG wg) {
        this.wg = wg;
    }

    public Double getTotalAmount() {
        return totalAmount;
    }

    public void setTotalAmount(Double totalAmount) {
        this.totalAmount = totalAmount;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public StandingOrderFrequency getFrequency() {
        return frequency;
    }

    public void setFrequency(StandingOrderFrequency frequency) {
        this.frequency = frequency;
    }

    public LocalDate getNextExecution() {
        return nextExecution;
    }

    public void setNextExecution(LocalDate nextExecution) {
        this.nextExecution = nextExecution;
    }

    public Boolean getIsActive() {
        return isActive;
    }

    public void setIsActive(Boolean isActive) {
        this.isActive = isActive;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public String getDebtorData() {
        return debtorData;
    }

    public void setDebtorData(String debtorData) {
        this.debtorData = debtorData;
    }

    /**
     * Advance the next execution date based on frequency
     */
    public void advanceNextExecution() {
        switch (frequency) {
            case WEEKLY:
                this.nextExecution = this.nextExecution.plusWeeks(1);
                break;
            case BI_WEEKLY:
                this.nextExecution = this.nextExecution.plusWeeks(2);
                break;
            case MONTHLY:
                this.nextExecution = calculateNextMonthlyExecution(this.nextExecution.plusMonths(1));
                break;
        }
    }

    /**
     * Calculate the next monthly execution date based on preferences.
     * Handles fallback when preferred day doesn't exist in the target month.
     */
    private LocalDate calculateNextMonthlyExecution(LocalDate targetMonth) {
        if (Boolean.TRUE.equals(monthlyLastDay)) {
            // Last day of month mode
            return targetMonth.withDayOfMonth(targetMonth.lengthOfMonth());
        } else if (monthlyDay != null && monthlyDay >= 1 && monthlyDay <= 31) {
            // Fixed day mode with fallback
            int daysInMonth = targetMonth.lengthOfMonth();
            int actualDay = Math.min(monthlyDay, daysInMonth);
            return targetMonth.withDayOfMonth(actualDay);
        } else {
            // Default: 1st of month
            return targetMonth.withDayOfMonth(1);
        }
    }

    // Monthly preference getters and setters
    public Integer getMonthlyDay() {
        return monthlyDay;
    }

    public void setMonthlyDay(Integer monthlyDay) {
        this.monthlyDay = monthlyDay;
    }

    public Boolean getMonthlyLastDay() {
        return monthlyLastDay;
    }

    public void setMonthlyLastDay(Boolean monthlyLastDay) {
        this.monthlyLastDay = monthlyLastDay;
    }
}

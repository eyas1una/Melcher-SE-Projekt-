package com.group_2.dto.finance;

/**
 * DTO representing a balance between two users. Positive = other user owes
 * current user. Negative = current user owes other user.
 */
public record BalanceDTO(Long userId, String userName, Double balance) {
    /**
     * Create a display-friendly balance string with Euro symbol
     */
    public String getFormattedBalance() {
        return String.format("%.2f€", Math.abs(balance));
    }

    /**
     * Check if the balance represents money owed TO the current user
     */
    public boolean isCredit() {
        return balance > 0;
    }

    /**
     * Check if the balance represents money owed BY the current user
     */
    public boolean isDebt() {
        return balance < 0;
    }

    /**
     * Check if the balance is settled (zero)
     */
    public boolean isSettled() {
        return Math.abs(balance) < 0.01;
    }

    /**
     * Get absolute balance value
     */
    public Double getAbsoluteBalance() {
        return Math.abs(balance);
    }

    /**
     * Get a status-aware formatted string
     */
    public String getStatusText() {
        if (isSettled()) {
            return "Settled";
        } else if (isCredit()) {
            return "owes you " + getFormattedBalance();
        } else {
            return "you owe " + getFormattedBalance();
        }
    }
}

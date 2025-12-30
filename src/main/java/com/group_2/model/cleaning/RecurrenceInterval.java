package com.group_2.model.cleaning;

/**
 * Enum representing the frequency of a cleaning task.
 */
public enum RecurrenceInterval {
    WEEKLY(1, "Weekly"),
    BI_WEEKLY(2, "Bi-weekly"),
    MONTHLY(4, "Monthly");

    private final int weeks;
    private final String displayName;

    RecurrenceInterval(int weeks, String displayName) {
        this.weeks = weeks;
        this.displayName = displayName;
    }

    public int getWeeks() {
        return weeks;
    }

    public String getDisplayName() {
        return displayName;
    }

    @Override
    public String toString() {
        return displayName;
    }
}

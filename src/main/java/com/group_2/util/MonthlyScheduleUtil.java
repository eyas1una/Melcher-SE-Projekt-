package com.group_2.util;

import java.time.LocalDate;
import java.time.Month;

public final class MonthlyScheduleUtil {
    private MonthlyScheduleUtil() {
    }

    public static LocalDate resolveMonthlyDate(LocalDate targetMonth, Integer monthlyDay, Boolean monthlyLastDay) {
        if (Boolean.TRUE.equals(monthlyLastDay)) {
            return targetMonth.withDayOfMonth(getEffectiveLastDay(targetMonth));
        }
        if (monthlyDay != null && monthlyDay >= 1 && monthlyDay <= 31) {
            return targetMonth.withDayOfMonth(getEffectiveDay(targetMonth, monthlyDay));
        }
        return targetMonth.withDayOfMonth(1);
    }

    public static int getEffectiveDay(LocalDate targetMonth, int preferredDay) {
        if (targetMonth.getMonth() == Month.FEBRUARY && preferredDay > 28) {
            return 28;
        }
        return Math.min(preferredDay, targetMonth.lengthOfMonth());
    }

    public static int getEffectiveLastDay(LocalDate targetMonth) {
        if (targetMonth.getMonth() == Month.FEBRUARY) {
            return 28;
        }
        return targetMonth.lengthOfMonth();
    }
}

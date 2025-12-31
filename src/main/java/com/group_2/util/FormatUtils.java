package com.group_2.util;

import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

/**
 * Centralized utility class for formatting values used across the application.
 * Provides consistent formatting for currency, dates, and ordinal suffixes.
 */
public final class FormatUtils {

    private static final DecimalFormat CURRENCY_FORMAT;
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("dd.MM.yyyy");
    private static final DateTimeFormatter SHORT_DATE_FORMATTER = DateTimeFormatter.ofPattern("dd.MM");

    static {
        DecimalFormatSymbols symbols = new DecimalFormatSymbols(Locale.GERMANY);
        symbols.setGroupingSeparator('.');
        symbols.setDecimalSeparator(',');
        CURRENCY_FORMAT = new DecimalFormat("€#,##0.00", symbols);
    }

    private FormatUtils() {
        // Utility class - prevent instantiation
    }

    /**
     * Format a monetary amount as currency (e.g., "€1.234,56").
     *
     * @param amount the amount to format
     * @return formatted currency string
     */
    public static String formatCurrency(double amount) {
        return CURRENCY_FORMAT.format(amount);
    }

    /**
     * Format a monetary amount as currency with sign indicator.
     * Positive amounts show "+", negative amounts show "-".
     *
     * @param amount the amount to format
     * @return formatted currency string with sign
     */
    public static String formatCurrencyWithSign(double amount) {
        String formatted = formatCurrency(Math.abs(amount));
        if (amount > 0) {
            return "+" + formatted;
        } else if (amount < 0) {
            return "-" + formatted;
        }
        return formatted;
    }

    /**
     * Get the ordinal suffix for a day number (e.g., "st", "nd", "rd", "th").
     * Used for displaying dates like "1st", "2nd", "3rd", "4th", etc.
     *
     * @param day the day number (1-31)
     * @return the ordinal suffix
     */
    public static String getDaySuffix(int day) {
        if (day >= 11 && day <= 13) {
            return "th";
        }
        switch (day % 10) {
            case 1:
                return "st";
            case 2:
                return "nd";
            case 3:
                return "rd";
            default:
                return "th";
        }
    }

    /**
     * Format a day with its ordinal suffix (e.g., "1st", "2nd", "23rd").
     *
     * @param day the day number
     * @return the day with ordinal suffix
     */
    public static String formatDayWithSuffix(int day) {
        return day + getDaySuffix(day);
    }

    /**
     * Format a date as "dd.MM.yyyy".
     *
     * @param date the date to format
     * @return formatted date string
     */
    public static String formatDate(LocalDate date) {
        if (date == null) {
            return "";
        }
        return date.format(DATE_FORMATTER);
    }

    /**
     * Format a date as short form "dd.MM".
     *
     * @param date the date to format
     * @return formatted short date string
     */
    public static String formatShortDate(LocalDate date) {
        if (date == null) {
            return "";
        }
        return date.format(SHORT_DATE_FORMATTER);
    }
}

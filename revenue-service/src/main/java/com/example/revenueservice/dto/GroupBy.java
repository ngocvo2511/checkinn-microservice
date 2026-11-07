package com.example.revenueservice.dto;

public enum GroupBy {
    DAY,
    MONTH,
    YEAR;

    public static GroupBy fromString(String value) {
        if (value == null) return DAY;
        return switch (value.trim().toLowerCase(java.util.Locale.ROOT)) {
            case "day" -> DAY;
            case "month" -> MONTH;
            case "year" -> YEAR;
            default -> throw new IllegalArgumentException("Invalid report grouping");
        };
    }
}

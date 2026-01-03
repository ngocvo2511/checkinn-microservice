package com.example.revenueservice.dto;

public enum GroupBy {
    DAY,
    MONTH,
    YEAR;

    public static GroupBy fromString(String value) {
        if (value == null) return DAY;
        return switch (value.toLowerCase()) {
            case "month" -> MONTH;
            case "year" -> YEAR;
            default -> DAY;
        };
    }
}

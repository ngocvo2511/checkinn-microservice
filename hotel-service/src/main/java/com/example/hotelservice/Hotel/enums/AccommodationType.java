package com.example.hotelservice.Hotel.enums;

import com.fasterxml.jackson.annotation.JsonCreator;

public enum AccommodationType {
    HOTEL("Hotel"), VILLA("Villa"), APARTMENT("Apartment"), GUEST_HOUSE("Guest House");
    private final String label;
    AccommodationType(String label) { this.label = label; }
    public String getLabel() { return label; }
    @JsonCreator
    public static AccommodationType parse(String code) {
        if (code == null) return null;
        try { return valueOf(code); }
        catch (IllegalArgumentException ex) { throw new IllegalArgumentException("Loại hình lưu trú không hợp lệ"); }
    }
}

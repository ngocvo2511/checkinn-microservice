package com.example.hotelservice.Hotel.dto.request;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

public record HotelOfferCriteria(UUID cityId, UUID provinceId, String cityName, String hotelName,
        @org.springframework.format.annotation.DateTimeFormat(iso = org.springframework.format.annotation.DateTimeFormat.ISO.DATE) LocalDate checkIn,
        @org.springframework.format.annotation.DateTimeFormat(iso = org.springframework.format.annotation.DateTimeFormat.ISO.DATE) LocalDate checkOut, Integer rooms, Integer adults, Integer children,
        BigDecimal minPrice, BigDecimal maxPrice, java.util.List<String> accommodationTypes, java.util.List<String> amenityCodes) {
    public boolean matchesType(com.example.hotelservice.Hotel.enums.AccommodationType type) {
        return accommodationTypes == null || accommodationTypes.isEmpty() || (type != null && accommodationTypes.contains(type.name()));
    }

    public int roomCount() { return rooms == null ? 1 : rooms; }
    public int adultCount() { return adults == null ? 1 : adults; }
    public int childCount() { return children == null ? 0 : children; }
    public void validate() {
        if (accommodationTypes != null) for (String code : accommodationTypes) {
            if (code == null || code.isBlank()) throw new IllegalArgumentException("Loại hình lưu trú không hợp lệ");
            com.example.hotelservice.Hotel.enums.AccommodationType.parse(code);
        }
        if (roomCount() < 1 || adultCount() < 1 || childCount() < 0) throw new IllegalArgumentException("Invalid room or guest count");
        if ((checkIn == null) != (checkOut == null) || (checkIn != null && !checkOut.isAfter(checkIn))) throw new IllegalArgumentException("Invalid stay dates");
        if ((minPrice != null && minPrice.signum() < 0) || (maxPrice != null && maxPrice.signum() < 0)
                || (minPrice != null && maxPrice != null && minPrice.compareTo(maxPrice) > 0)) throw new IllegalArgumentException("Invalid price range");
        if (cityId == null && provinceId == null && (cityName == null || cityName.isBlank()) && (hotelName == null || hotelName.isBlank()))
            throw new IllegalArgumentException("A destination or hotel name is required");
    }
}

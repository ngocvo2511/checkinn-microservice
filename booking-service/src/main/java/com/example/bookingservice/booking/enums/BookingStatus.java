package com.example.bookingservice.booking.enums;

public enum BookingStatus {
    PENDING,              // Chờ thanh toán
    PENDING_PAYMENT,      // Chờ thanh toán (VNPay)
    CONFIRMED,            // Đã xác nhận
    CHECKED_IN,           // Đã nhận phòng
    CHECKED_OUT,          // Đã trả phòng
    CANCELLED,            // Đã hủy
    NO_SHOW               // Không xuất hiện
}

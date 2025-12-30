package com.example.bookingservice.payment.enums;

public enum PaymentStatus {
    PENDING,            // Chờ thanh toán
    ONSITE_PENDING,     // Giữ chỗ, chờ thanh toán tại khách sạn
    COMPLETED,          // Đã thanh toán
    FAILED,             // Thanh toán thất bại
    REFUNDED,           // Đã hoàn tiền
    CANCELLED           // Đã hủy
}

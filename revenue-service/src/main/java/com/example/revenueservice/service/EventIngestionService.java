package com.example.revenueservice.service;

import com.example.revenueservice.entity.BookingStatusRecord;
import com.example.revenueservice.entity.PaymentRecord;
import com.example.revenueservice.messaging.event.BookingStatusEvent;
import com.example.revenueservice.messaging.event.PaymentEvent;
import com.example.revenueservice.repository.BookingStatusRecordRepository;
import com.example.revenueservice.repository.PaymentRecordRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
public class EventIngestionService {

    private final PaymentRecordRepository paymentRecordRepository;
    private final BookingStatusRecordRepository bookingStatusRecordRepository;

    public EventIngestionService(PaymentRecordRepository paymentRecordRepository,
                                 BookingStatusRecordRepository bookingStatusRecordRepository) {
        this.paymentRecordRepository = paymentRecordRepository;
        this.bookingStatusRecordRepository = bookingStatusRecordRepository;
    }

    @Transactional
    public void savePaymentEvent(PaymentEvent event) {
        PaymentRecord record = paymentRecordRepository.findByBookingId(event.bookingId())
                .orElse(new PaymentRecord());
        
        // Set or update all fields
        record.setBookingId(event.bookingId());
        record.setHotelId(event.hotelId());
        record.setRoomTypeId(event.roomTypeId());
        record.setCheckInDate(event.checkInDate());
        record.setCheckOutDate(event.checkOutDate());
        record.setNights(event.nights());
        record.setRooms(event.rooms());
        record.setAmount(event.amount());
        record.setPaymentStatus(event.paymentStatus());
        record.setPaymentMethod(event.paymentMethod());
        record.setPaidAt(event.paidAt());
        record.setEventAt(event.eventAt() != null ? event.eventAt() : LocalDateTime.now());
        
        // Only set createdAt if it's a new record
        if (record.getCreatedAt() == null) {
            record.setCreatedAt(LocalDateTime.now());
        }
        
        paymentRecordRepository.save(record);
    }

    @Transactional
    public void saveBookingStatusEvent(BookingStatusEvent event) {
        BookingStatusRecord record = bookingStatusRecordRepository.findByBookingId(event.bookingId())
                .orElse(new BookingStatusRecord());
        
        // Set or update all fields
        record.setBookingId(event.bookingId());
        record.setHotelId(event.hotelId());
        record.setRoomTypeId(event.roomTypeId());
        record.setCheckInDate(event.checkInDate());
        record.setCheckOutDate(event.checkOutDate());
        record.setNights(event.nights());
        record.setRooms(event.rooms());
        record.setBookingStatus(event.bookingStatus());
        record.setEventAt(event.eventAt() != null ? event.eventAt() : LocalDateTime.now());
        
        // Only set createdAt if it's a new record
        if (record.getCreatedAt() == null) {
            record.setCreatedAt(LocalDateTime.now());
        }
        
        bookingStatusRecordRepository.save(record);
    }
}

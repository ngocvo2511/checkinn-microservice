package com.example.revenueservice.service;

import com.example.revenueservice.entity.BookingStatusRecord;
import com.example.revenueservice.entity.PaymentRecord;
import com.example.revenueservice.messaging.event.BookingStatusEvent;
import com.example.revenueservice.messaging.event.PaymentEvent;
import com.example.revenueservice.repository.BookingStatusRecordRepository;
import com.example.revenueservice.repository.PaymentRecordRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
public class EventIngestionService {

    private static final Logger logger = LoggerFactory.getLogger(EventIngestionService.class);

    private final PaymentRecordRepository paymentRecordRepository;
    private final BookingStatusRecordRepository bookingStatusRecordRepository;

    public EventIngestionService(PaymentRecordRepository paymentRecordRepository,
                                 BookingStatusRecordRepository bookingStatusRecordRepository) {
        this.paymentRecordRepository = paymentRecordRepository;
        this.bookingStatusRecordRepository = bookingStatusRecordRepository;
    }

    @Transactional
    public void savePaymentEvent(PaymentEvent event) {
        try {
            logger.info("[REVENUE_PAYMENT_INGEST_START] Ingesting payment event - bookingId: {}, hotelId: {}, amount: {}",
                    event.bookingId(), event.hotelId(), event.amount());
            PaymentRecord record = paymentRecordRepository.findByBookingId(event.bookingId())
                    .orElse(new PaymentRecord());

            // Set or update all fields
            record.setBookingId(event.bookingId());
            record.setCustomerId(event.customerId());
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

            PaymentRecord saved = paymentRecordRepository.save(record);
        logger.info("[REVENUE_PAYMENT_INGEST_SUCCESS] PaymentRecord saved - id: {}, bookingId: {}, status: {}",
            saved.getId(), saved.getBookingId(), saved.getPaymentStatus());
        } catch (Exception e) {
        logger.error("[REVENUE_PAYMENT_INGEST_ERROR] Failed to ingest payment event - bookingId: {}, error: {}",
            event.bookingId(), e.getMessage(), e);
            throw e;
        }
    }

    @Transactional
    public void saveBookingStatusEvent(BookingStatusEvent event) {
        try {
        logger.info("[REVENUE_BOOKING_STATUS_INGEST_START] Ingesting booking status event - bookingId: {}, hotelId: {}, status: {}",
            event.bookingId(), event.hotelId(), event.bookingStatus());
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

            BookingStatusRecord saved = bookingStatusRecordRepository.save(record);
            logger.info("[REVENUE_BOOKING_STATUS_INGEST_SUCCESS] BookingStatusRecord saved - id: {}, bookingId: {}, status: {}",
                    saved.getId(), saved.getBookingId(), saved.getBookingStatus());
        } catch (Exception e) {
            logger.error("[REVENUE_BOOKING_STATUS_INGEST_ERROR] Failed to ingest booking status event - bookingId: {}, error: {}",
                    event.bookingId(), e.getMessage(), e);
            throw e;
        }
    }
}

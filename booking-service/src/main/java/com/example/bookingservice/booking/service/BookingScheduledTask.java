package com.example.bookingservice.booking.service;

import com.example.bookingservice.booking.entity.Booking;
import com.example.bookingservice.booking.enums.BookingStatus;
import com.example.bookingservice.booking.repository.BookingRepository;
import com.example.bookingservice.payment.entity.Payment;
import com.example.bookingservice.payment.enums.PaymentStatus;
import com.example.bookingservice.payment.repository.PaymentRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Scheduled tasks for booking status management.
 * Handles automatic status updates for expired and checked-out bookings.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class BookingScheduledTask {

    private final BookingRepository bookingRepository;
    private final PaymentRepository paymentRepository;

    /**
     * Runs every 5 minutes to process bookings that have exceeded their hold duration.
     * Transitions expired PENDING or PENDING_PAYMENT bookings to CANCELLED.
     * 
     * Timeline:
     * - User creates booking: PENDING, holdExpiresAt = +15 min (time to choose payment method)
     * - User chooses VNPay: PENDING_PAYMENT, holdExpiresAt reset to +15 min (time to pay)
     * - If either expires: CANCELLED
     */
    @Scheduled(cron = "0 */5 * * * *") // Every 5 minutes
    @Transactional
    public void processExpiredPaymentHolds() {
        log.debug("Running scheduled task: processExpiredPaymentHolds");
        
        LocalDateTime expiryThreshold = LocalDateTime.now();
        
        // Find all PENDING and PENDING_PAYMENT bookings with holdExpiresAt in the past
        List<Booking> pendingBookings = bookingRepository.findByStatus(BookingStatus.PENDING);
        List<Booking> pendingPaymentBookings = bookingRepository.findByStatus(BookingStatus.PENDING_PAYMENT);
        
        List<Booking> allPendingBookings = new ArrayList<>();
        allPendingBookings.addAll(pendingBookings);
        allPendingBookings.addAll(pendingPaymentBookings);
        
        int cancelledCount = 0;
        for (Booking booking : allPendingBookings) {
            if (booking.getHoldExpiresAt() != null && 
                booking.getHoldExpiresAt().isBefore(expiryThreshold)) {
                
                booking.setStatus(BookingStatus.CANCELLED);
                bookingRepository.save(booking);
                
                // Clean up associated payment if exists
                paymentRepository.findByBookingId(booking.getId()).ifPresent(payment -> {
                    if (payment.getStatus() == PaymentStatus.PENDING) {
                        payment.setStatus(PaymentStatus.CANCELLED);
                        paymentRepository.save(payment);
                        log.info("Cancelled payment {} for expired booking {}", payment.getId(), booking.getId());
                    }
                });
                
                cancelledCount++;
                log.info("Cancelled expired hold for booking: {} (status: {}, holdExpiresAt: {})", 
                         booking.getId(), booking.getStatus(), booking.getHoldExpiresAt());
            }
        }
        
        if (cancelledCount > 0) {
            log.info("Processed {} expired holds", cancelledCount);
        }
    }

    /**
     * Runs daily at midnight to auto-complete checked-out bookings.
     * Transitions CONFIRMED and CHECKED_IN bookings with past checkOutDate to CHECKED_OUT.
     */
    @Scheduled(cron = "0 0 0 * * *") // Every day at midnight
    @Transactional
    public void processAutoCheckOut() {
        log.debug("Running scheduled task: processAutoCheckOut");
        
        LocalDate today = LocalDate.now();
        
        // Find all CONFIRMED or CHECKED_IN bookings with checkOutDate in the past
        List<Booking> confirmedBookings = bookingRepository.findByStatus(BookingStatus.CONFIRMED);
        List<Booking> checkedInBookings = bookingRepository.findByStatus(BookingStatus.CHECKED_IN);
        
        confirmedBookings.addAll(checkedInBookings);
        
        int checkedOutCount = 0;
        for (Booking booking : confirmedBookings) {
            // Safety check: only process CONFIRMED or CHECKED_IN
            if (booking.getStatus() != BookingStatus.CONFIRMED && 
                booking.getStatus() != BookingStatus.CHECKED_IN) {
                log.warn("Skipping auto check-out for booking {} with unexpected status: {}", 
                         booking.getId(), booking.getStatus());
                continue;
            }
            
            if (booking.getCheckOutDate() != null && 
                booking.getCheckOutDate().isBefore(today)) {
                
                booking.setStatus(BookingStatus.CHECKED_OUT);
                bookingRepository.save(booking);
                checkedOutCount++;
                log.info("Auto-checked-out booking: {} (checkOutDate: {})", 
                         booking.getId(), booking.getCheckOutDate());
            }
        }
        
        if (checkedOutCount > 0) {
            log.info("Processed {} auto check-outs", checkedOutCount);
        }
    }

    /**
     * Runs every 15 minutes during the afternoon/evening window to auto check-in guests.
     * Transitions CONFIRMED bookings with checkInDate == today and current time >= 14:00 to CHECKED_IN.
     */
    @Scheduled(cron = "0 */15 14-23 * * *") // Every 15 minutes from 14:00 to 23:45
    @Transactional
    public void processAutoCheckIn() {
        log.debug("Running scheduled task: processAutoCheckIn");

        LocalDate today = LocalDate.now();
        LocalTime nowTime = LocalTime.now();
        LocalTime checkInStart = LocalTime.of(14, 0);

        // Guard: only proceed after check-in window starts
        if (nowTime.isBefore(checkInStart)) {
            return;
        }

        List<Booking> confirmedBookings = bookingRepository.findByStatus(BookingStatus.CONFIRMED);

        int checkedInCount = 0;
        for (Booking booking : confirmedBookings) {
            // Double-check status before updating (safety check)
            if (booking.getStatus() != BookingStatus.CONFIRMED) {
                log.warn("Skipping auto check-in for booking {} with unexpected status: {}", 
                         booking.getId(), booking.getStatus());
                continue;
            }
            
            if (booking.getCheckInDate() != null && booking.getCheckInDate().isEqual(today)) {
                booking.setStatus(BookingStatus.CHECKED_IN);
                bookingRepository.save(booking);
                checkedInCount++;
                log.info("Auto-checked-in booking: {} (checkInDate: {})", booking.getId(), booking.getCheckInDate());
            }
        }

        if (checkedInCount > 0) {
            log.info("Processed {} auto check-ins", checkedInCount);
        }
    }

    /**
     * Runs daily to mark NO_SHOW for bookings that never checked in by the day after their check-in date.
     */
    @Scheduled(cron = "0 0 2 * * *") // Every day at 2 AM
    @Transactional
    public void processNoShow() {
        log.debug("Running scheduled task: processNoShow");

        LocalDate today = LocalDate.now();

        // Find all CONFIRMED bookings with checkInDate before today (missed check-in day)
        List<Booking> confirmedBookings = bookingRepository.findByStatus(BookingStatus.CONFIRMED);

        int noShowCount = 0;
        for (Booking booking : confirmedBookings) {
            // Safety check: only process CONFIRMED bookings
            if (booking.getStatus() != BookingStatus.CONFIRMED) {
                log.warn("Skipping no-show check for booking {} with unexpected status: {}", 
                         booking.getId(), booking.getStatus());
                continue;
            }
            
            if (booking.getCheckInDate() != null && booking.getCheckInDate().isBefore(today)) {
                booking.setStatus(BookingStatus.NO_SHOW);
                bookingRepository.save(booking);
                noShowCount++;
                log.info("Marked NO_SHOW for booking: {} (checkInDate: {})", booking.getId(), booking.getCheckInDate());
            }
        }

        if (noShowCount > 0) {
            log.info("Processed {} no-show bookings", noShowCount);
        }
    }
}

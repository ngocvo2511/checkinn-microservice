package com.example.bookingservice.booking.service;

import java.util.UUID;
import com.example.bookingservice.integration.hotel.HotelAvailabilityClient;
import com.example.bookingservice.integration.hotel.dto.HoldResponse;
import com.example.bookingservice.integration.loyalty.LoyaltyPointsClient;
import org.springframework.transaction.support.TransactionSynchronization;
import java.util.concurrent.atomic.AtomicBoolean;

/** Compensate known creation resources, including a commit failure outside the method body. */
final class BookingCreationCompensation implements TransactionSynchronization,Runnable {
    private final HotelAvailabilityClient inventory;
    private final LoyaltyPointsClient points;
    private final HoldResponse hold;
    private final UUID userId;
    private UUID pointsBookingId;
    private final AtomicBoolean running=new AtomicBoolean();
    private boolean inventoryReleased,pointsReleased;
    BookingCreationCompensation(HotelAvailabilityClient inventory,LoyaltyPointsClient points,HoldResponse hold,UUID userId) {
        this.inventory=inventory;this.points=points;this.hold=hold;this.userId=userId;
    }
    void reservedPointsFor(UUID id) {pointsBookingId=id;}
    @Override public void afterCompletion(int status) {
        // UNKNOWN may have committed: never release another valid reservation on that assumption.
        if(status==STATUS_ROLLED_BACK)run();
    }
    @Override public void run() {
        if(!running.compareAndSet(false,true))return;
        try {
            if(!inventoryReleased && hold!=null && hold.holdId()!=null)try {
                inventory.releaseHold(hold.holdId());inventoryReleased=true;
            } catch(RuntimeException unavailable) {
                org.slf4j.LoggerFactory.getLogger(BookingCreationCompensation.class).warn("[BOOKING_CREATE_INVENTORY_CLEANUP_PENDING]");
            }
            if(!pointsReleased && pointsBookingId!=null)try {
                points.refundPoints(userId,pointsBookingId);pointsReleased=true;
            } catch(RuntimeException unavailable) {
                org.slf4j.LoggerFactory.getLogger(BookingCreationCompensation.class).warn("[BOOKING_CREATE_POINTS_CLEANUP_PENDING]");
            }
        } finally {running.set(false);}
    }
}

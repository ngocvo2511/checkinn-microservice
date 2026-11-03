package com.example.bookingservice.booking.service;

import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

/** Prevent an uncommitted points-bearing booking from being mistaken for an orphan. */
@Repository
@RequiredArgsConstructor
public class BookingCreationLock {
    private final EntityManager entityManager;

    public boolean tryAcquire(String bookingId) {
        return Boolean.TRUE.equals(entityManager.createNativeQuery(
                "select pg_try_advisory_xact_lock(hashtextextended(cast(:key as text), 0))")
                .setParameter("key", "points-booking-create:" + bookingId).getSingleResult());
    }
}

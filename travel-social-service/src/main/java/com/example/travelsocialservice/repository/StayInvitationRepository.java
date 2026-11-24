package com.example.travelsocialservice.repository;

import com.example.travelsocialservice.domain.InvitationStatus;
import com.example.travelsocialservice.domain.StayInvitation;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface StayInvitationRepository extends JpaRepository<StayInvitation, UUID> {

    /** Returns 0 when the booking already has an invitation in any status, which is never reset. */
    @Modifying
    @Query(value = """
            INSERT INTO stay_invitations (booking_id, user_id, hotel_id, check_out_date, status, created_at, expires_at, status_changed_at)
            VALUES (:bookingId, :userId, :hotelId, :checkOutDate, 'OPEN', :createdAt, :expiresAt, :createdAt)
            ON CONFLICT (booking_id) DO NOTHING
            """, nativeQuery = true)
    int insertIfAbsent(@Param("bookingId") UUID bookingId, @Param("userId") UUID userId, @Param("hotelId") UUID hotelId,
                       @Param("checkOutDate") LocalDate checkOutDate, @Param("createdAt") Instant createdAt,
                       @Param("expiresAt") Instant expiresAt);

    List<StayInvitation> findByUserIdAndStatusAndExpiresAtAfterOrderByCheckOutDateDescBookingIdDesc(
            UUID userId, InvitationStatus status, Instant now, Pageable pageable);

    Optional<StayInvitation> findByBookingIdAndUserId(UUID bookingId, UUID userId);

    @Modifying
    @Query("""
            update StayInvitation i set i.status = com.example.travelsocialservice.domain.InvitationStatus.EXPIRED,
                   i.statusChangedAt = :now, i.version = i.version + 1
            where i.status = com.example.travelsocialservice.domain.InvitationStatus.OPEN and i.expiresAt <= :now
            """)
    int expireOverdue(@Param("now") Instant now);

    @Modifying
    @Query("""
            delete from StayInvitation i
            where i.status <> com.example.travelsocialservice.domain.InvitationStatus.OPEN and i.statusChangedAt < :cutoff
            """)
    int deleteClosedBefore(@Param("cutoff") Instant cutoff);
}

package com.example.travelsocialservice.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/** One invitation per completed booking; the booking id is the key, so replayed events cannot add rows. */
@Entity
@Table(name = "stay_invitations")
@Getter
@Setter
@NoArgsConstructor
public class StayInvitation {

    @Id
    @Column(name = "booking_id")
    private UUID bookingId;

    @Column(name = "user_id", nullable = false, updatable = false)
    private UUID userId;

    @Column(name = "hotel_id", nullable = false, updatable = false)
    private UUID hotelId;

    @Column(name = "check_out_date", nullable = false, updatable = false)
    private LocalDate checkOutDate;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private InvitationStatus status;

    @Column(name = "post_id")
    private UUID postId;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "status_changed_at", nullable = false)
    private Instant statusChangedAt;

    @Version
    @Column(nullable = false)
    private long version;

    public boolean isOpenAt(Instant now) {
        return status == InvitationStatus.OPEN && expiresAt.isAfter(now);
    }

    public void changeStatus(InvitationStatus next, Instant at) {
        this.status = next;
        this.statusChangedAt = at;
    }
}

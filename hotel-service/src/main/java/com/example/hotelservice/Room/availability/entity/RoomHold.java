package com.example.hotelservice.Room.availability.entity;

import com.example.hotelservice.Room.availability.enums.HoldStatus;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "room_holds")
@Getter
@Setter
public class RoomHold {

    @Id
    @GeneratedValue
    private UUID id;

    @Column(name = "room_type_id", nullable = false)
    private UUID roomTypeId;

    @Column(name = "hotel_id", nullable = false)
    private UUID hotelId;

    @Column(name = "check_in_date", nullable = false)
    private LocalDate checkInDate;

    @Column(name = "check_out_date", nullable = false)
    private LocalDate checkOutDate;

    @Column(name = "quantity", nullable = false)
    private Integer quantity;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private HoldStatus status;

    @Column(name = "expires_at")
    private Instant expiresAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @jakarta.persistence.PrePersist
    public void prePersist() {
        if (id == null) id = UUID.randomUUID();
        if (status == null) status = HoldStatus.HELD;
        this.createdAt = Instant.now();
        this.updatedAt = this.createdAt;
    }

    @jakarta.persistence.PreUpdate
    public void preUpdate() {
        this.updatedAt = Instant.now();
    }
}

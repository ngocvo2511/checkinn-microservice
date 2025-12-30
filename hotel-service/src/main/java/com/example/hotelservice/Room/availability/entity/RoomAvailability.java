package com.example.hotelservice.Room.availability.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "room_availability", uniqueConstraints = {
        @UniqueConstraint(columnNames = {"room_type_id", "date"})
})
@Getter
@Setter
public class RoomAvailability {

    @Id
    @GeneratedValue
    private UUID id;

    @Column(name = "room_type_id", nullable = false)
    private UUID roomTypeId;

    @Column(name = "date", nullable = false)
    private LocalDate date;

    @Column(name = "held", nullable = false)
    private Integer held = 0;

    @Column(name = "booked", nullable = false)
    private Integer booked = 0;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @jakarta.persistence.PrePersist
    public void prePersist() {
        if (id == null) id = UUID.randomUUID();
        this.createdAt = Instant.now();
        this.updatedAt = this.createdAt;
    }

    @jakarta.persistence.PreUpdate
    public void preUpdate() {
        this.updatedAt = Instant.now();
    }
}

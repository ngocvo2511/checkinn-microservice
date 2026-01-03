package com.example.hotelservice.Amenity.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "hotel_amenities")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class HotelAmenity {
    @Id
    @GeneratedValue
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "category_id", nullable = false)
    private HotelAmenityCategory category;

    @Column(nullable = false, length = 255)
    private String title;

    @Builder.Default
    @Column(name = "is_active", nullable = false) private Boolean isActive = true;
    @Column(name = "created_at", nullable = false) private Instant createdAt = Instant.now();
}
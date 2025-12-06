package com.example.hotelservice.Room.entity;

import com.example.hotelservice.Hotel.entity.Hotel;
import com.example.hotelservice.MediaAsset.entity.MediaAsset;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "room_types")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RoomType {

    @Id
    @GeneratedValue
    private UUID id;

    @Column(name = "hotel_id", nullable = false)
    private UUID hotelId;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(name = "base_price", nullable = false)
    private BigDecimal basePrice;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb", nullable = false)
    private String capacity;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private String amenities;

    @Column(name = "is_active", nullable = false)
    private Boolean isActive = true;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;


    // ===================
    // ROOMTYPE N - 1 HOTEL (optional)
    // ===================
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "hotel_id", insertable = false, updatable = false)
    private Hotel hotel;


    // ===================
    // ROOMTYPE 1 - N MEDIA_ASSET (targetType = ROOM_TYPE)
    // ===================
    @OneToMany(fetch = FetchType.LAZY)
    @JoinColumn(name = "target_id", referencedColumnName = "id", insertable = false, updatable = false)
    private List<MediaAsset> mediaAssets;


    @PrePersist
    public void prePersist() {
        if (id == null) id = UUID.randomUUID();
        this.createdAt = Instant.now();
        this.updatedAt = Instant.now();
    }

    @PreUpdate
    public void preUpdate() {
        this.updatedAt = Instant.now();
    }
}



package com.example.hotelservice.Hotel.entity;

import com.example.hotelservice.City.entity.City;
import com.example.hotelservice.Hotel.enums.HotelApprovalStatus;
import com.example.hotelservice.MediaAsset.entity.MediaAsset;
import com.example.hotelservice.Room.entity.RoomType;
import jakarta.persistence.*;
import jakarta.persistence.ConstraintMode;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.Where;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "hotels")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Hotel {

    @Id
    @GeneratedValue
    private UUID id;

    @Column(name = "owner_id", nullable = false)
    private UUID ownerId;

    @Column(name = "city_id", nullable = false)
    private UUID cityId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "city_id", referencedColumnName = "id", insertable = false, updatable = false)
    private City city;

    @Column(nullable = false, length = 255)
    private String name;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(name = "star_rating")
    private Short starRating;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb", nullable = false)
    private String address;

    @Column(name = "business_license_number", length = 50, nullable = false)
    private String businessLicenseNumber;

    @Column(name = "tax_id", length = 50)
    private String taxId;

    @Column(name = "operation_license_number", length = 50)
    private String operationLicenseNumber;

    @Column(name = "owner_identity_number", length = 50)
    private String ownerIdentityNumber;

    @Column(name = "is_active", nullable = false)
    private Boolean isActive;

    @Enumerated(EnumType.STRING)
    @Column(name = "approved_status", nullable = false, length = 20)
    private HotelApprovalStatus approvedStatus;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;


    // ===================
    // HOTEL 1 - N ROOMTYPE
    // ===================
        @OneToMany(fetch = FetchType.LAZY)
        @JoinColumn(name = "hotel_id", referencedColumnName = "id", insertable = false, updatable = false,
            foreignKey = @ForeignKey(ConstraintMode.NO_CONSTRAINT))
        private List<RoomType> roomTypes;


    // ===================
    // HOTEL 1 - N MEDIA_ASSET (targetType = HOTEL)
    // ===================
        @OneToMany(fetch = FetchType.LAZY)
        @JoinColumn(name = "target_id", referencedColumnName = "id", insertable = false, updatable = false,
            foreignKey = @ForeignKey(ConstraintMode.NO_CONSTRAINT))
        @Where(clause = "target_type = 'HOTEL'")
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



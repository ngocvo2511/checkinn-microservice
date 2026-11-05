package com.example.hotelservice.Hotel.entity;
import com.example.hotelservice.Hotel.enums.*;
import jakarta.persistence.*;
import lombok.*;
import java.time.Instant;
import java.util.UUID;

@Entity @Table(name="hotel_accommodation_type_changes")
@Getter @Setter @NoArgsConstructor
public class AccommodationTypeChange {
    @Id private UUID id;
    @Column(nullable=false) private UUID hotelId;
    @Column(nullable=false) private UUID requesterId;
    @Enumerated(EnumType.STRING) @Column(length=32) private AccommodationType previousType;
    @Enumerated(EnumType.STRING) @Column(nullable=false,length=32) private AccommodationType requestedType;
    @Enumerated(EnumType.STRING) @Column(nullable=false,length=20) private AccommodationChangeStatus status;
    @Column(nullable=false) private Instant createdAt;
    private Instant reviewedAt;
    private UUID reviewerId;
    @Column(length=1000) private String rejectionReason;
    public String getPreviousTypeLabel() { return previousType == null ? null : previousType.getLabel(); }
    public String getRequestedTypeLabel() { return requestedType == null ? null : requestedType.getLabel(); }
}

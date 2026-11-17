package com.example.hotelservice.Room.repository;

import com.example.hotelservice.Room.entity.RoomType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import jakarta.persistence.LockModeType;
import java.util.Optional;

import java.util.List;
import java.util.UUID;

public interface RoomTypeRepository extends JpaRepository<RoomType, UUID> {
    // Pending/rejected proposals advance workflow revision without replacing approval.
    @Query(value = """
        SELECT EXISTS (SELECT 1 FROM room_types r
        JOIN room_inventory_review_states s ON s.room_id=r.id
        JOIN room_inventory_review_requests a ON a.id=s.approved_request_id
        WHERE r.id=?1 AND r.total_rooms>0 AND s.revision>=2
          AND a.room_id=r.id AND a.hotel_id=r.hotel_id
          AND a.status='APPROVED' AND a.proposed_quantity=r.total_rooms
          AND a.requested_by IS NOT NULL AND a.requested_at IS NOT NULL
          AND a.reviewed_by IS NOT NULL AND a.reviewed_at>=a.requested_at
          AND length(trim(a.source))>0 AND length(trim(a.review_reason))>0
          AND NOT EXISTS (SELECT 1 FROM room_inventory_review_requests newer
              WHERE newer.room_id=r.id AND newer.status='APPROVED'
                AND newer.reviewed_at>a.reviewed_at))
        """, nativeQuery = true)
    boolean hasEffectiveInventoryApproval(UUID roomId);

    @Query(value = """
        SELECT r.id FROM room_types r
        JOIN room_inventory_review_states s ON s.room_id=r.id
        JOIN room_inventory_review_requests a ON a.id=s.approved_request_id
        WHERE r.id IN (?1) AND r.total_rooms>0 AND s.revision>=2
          AND a.room_id=r.id AND a.hotel_id=r.hotel_id
          AND a.status='APPROVED' AND a.proposed_quantity=r.total_rooms
          AND a.requested_by IS NOT NULL AND a.requested_at IS NOT NULL
          AND a.reviewed_by IS NOT NULL AND a.reviewed_at>=a.requested_at
          AND length(trim(a.source))>0 AND length(trim(a.review_reason))>0
          AND NOT EXISTS (SELECT 1 FROM room_inventory_review_requests newer
              WHERE newer.room_id=r.id AND newer.status='APPROVED'
                AND newer.reviewed_at>a.reviewed_at)
        """, nativeQuery = true)
    List<UUID> findEffectivelyApprovedRoomIds(java.util.Collection<UUID> roomIds);


    @Query("SELECT r.hotelId FROM RoomType r WHERE r.id = ?1")
    Optional<UUID> findHotelIdById(UUID id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT r FROM RoomType r WHERE r.id = ?1")
    Optional<RoomType> findByIdForUpdate(UUID id);
    List<RoomType> findByHotelId(UUID hotelId);

    List<RoomType> findByHotelIdInAndIsActiveTrue(java.util.Collection<UUID> hotelIds);

    boolean existsByIdAndHotelId(UUID roomTypeId, UUID hotelId);
}

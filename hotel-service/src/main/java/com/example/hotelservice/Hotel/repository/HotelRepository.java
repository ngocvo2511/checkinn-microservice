package com.example.hotelservice.Hotel.repository;

import com.example.hotelservice.Hotel.entity.Hotel;
import com.example.hotelservice.Hotel.enums.HotelApprovalStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface HotelRepository extends JpaRepository<Hotel, UUID> {
    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @org.springframework.data.jpa.repository.Query("select h from Hotel h where h.id = :id")
    java.util.Optional<Hotel> findForUpdate(@org.springframework.data.repository.query.Param("id") UUID id);

    List<Hotel> findByIsActiveTrueAndApprovedStatus(HotelApprovalStatus status);

    List<Hotel> findByCityIdAndIsActiveTrueAndApprovedStatus(UUID cityId, HotelApprovalStatus status);

    List<Hotel> findByNameContainingIgnoreCaseAndIsActiveTrueAndApprovedStatus(String name, HotelApprovalStatus status);

    // Lấy tất cả hotel của 1 owner
    List<Hotel> findByOwnerId(UUID ownerId);

    // Admin: lấy hotel theo trạng thái duyệt
    List<Hotel> findByApprovedStatus(HotelApprovalStatus status);

    // Tìm hotel theo thành phố
    List<Hotel> findByCityId(UUID cityId);

    // Tìm hotel theo tên (case-insensitive)
    List<Hotel> findByNameContainingIgnoreCase(String name);

    // Tìm hotel theo owner và thành phố
    List<Hotel> findByOwnerIdAndCityId(UUID ownerId, UUID cityId);

    // Kiểm tra quyền sở hữu
    boolean existsByIdAndOwnerId(UUID id, UUID ownerId);
}

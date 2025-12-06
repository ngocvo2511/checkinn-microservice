package com.example.hotelservice.Hotel.repository;

import com.example.hotelservice.Hotel.entity.Hotel;
import com.example.hotelservice.Hotel.enums.HotelApprovalStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface HotelRepository extends JpaRepository<Hotel, UUID> {
    // Lấy tất cả hotel của 1 owner
    List<Hotel> findByOwnerId(UUID ownerId);

    // Admin: lấy hotel theo trạng thái duyệt
    List<Hotel> findByApprovedStatus(HotelApprovalStatus status);

    // Kiểm tra quyền sở hữu
    boolean existsByIdAndOwnerId(UUID id, UUID ownerId);;
}

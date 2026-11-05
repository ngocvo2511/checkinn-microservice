package com.example.hotelservice.Hotel.repository;
import com.example.hotelservice.Hotel.entity.AccommodationTypeChange;
import com.example.hotelservice.Hotel.enums.AccommodationChangeStatus;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import java.util.*;
public interface AccommodationTypeChangeRepository extends JpaRepository<AccommodationTypeChange,UUID> {
    Optional<AccommodationTypeChange> findByHotelIdAndStatus(UUID hotelId, AccommodationChangeStatus status);
    List<AccommodationTypeChange> findByHotelIdOrderByCreatedAtDesc(UUID hotelId);
    List<AccommodationTypeChange> findByStatusOrderByCreatedAtAsc(AccommodationChangeStatus status);
    @Query("select c.hotelId from AccommodationTypeChange c where c.id=:id")
    Optional<UUID> hotelIdFor(@Param("id") UUID id);
    @Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from AccommodationTypeChange c where c.id=:id")
    Optional<AccommodationTypeChange> findForUpdate(@Param("id") UUID id);
}

package com.example.placeservice.repository;

import com.example.placeservice.entity.PlacePhoto;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

@Repository
public interface PlacePhotoRepository extends JpaRepository<PlacePhoto, UUID> {

    List<PlacePhoto> findByPlaceIdOrderByDisplayOrderAsc(UUID placeId);

    List<PlacePhoto> findByPlace_IdInOrderByDisplayOrderAsc(Collection<UUID> placeIds);
}

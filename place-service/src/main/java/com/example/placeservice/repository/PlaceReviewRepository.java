package com.example.placeservice.repository;

import com.example.placeservice.entity.PlaceReview;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

@Repository
public interface PlaceReviewRepository extends JpaRepository<PlaceReview, UUID> {

    List<PlaceReview> findByPlaceIdOrderByCreatedAtDesc(UUID placeId);

    List<PlaceReview> findByPlace_IdInOrderByCreatedAtDesc(Collection<UUID> placeIds);
}

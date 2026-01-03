package com.example.hotelservice.Amenity.repository;

import com.example.hotelservice.Amenity.entity.CategoryType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.domain.Sort;

import java.util.List;
import java.util.UUID;

public interface CategoryTypeRepository extends JpaRepository<CategoryType, UUID> {
    default List<CategoryType> findAllSorted() {
        return findAll(Sort.by(Sort.Direction.ASC, "name"));
    }
}

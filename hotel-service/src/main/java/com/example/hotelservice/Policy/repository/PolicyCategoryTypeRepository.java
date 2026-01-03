package com.example.hotelservice.Policy.repository;

import com.example.hotelservice.Policy.entity.PolicyCategoryType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.domain.Sort;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PolicyCategoryTypeRepository extends JpaRepository<PolicyCategoryType, UUID> {
    default List<PolicyCategoryType> findAllSorted() {
        return findAll(Sort.by(Sort.Direction.ASC, "name"));
    }
    
    Optional<PolicyCategoryType> findByName(String name);
}

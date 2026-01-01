package com.example.hotelservice.Amenity.service;

import com.example.hotelservice.Amenity.dto.response.CategoryResponse;
import com.example.hotelservice.Amenity.repository.CategoryTypeRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class AmenityCategoryService {

    private final CategoryTypeRepository categoryTypeRepository;

    public List<CategoryResponse> getAvailableCategories() {
        return categoryTypeRepository.findAllSorted()
                .stream()
                .map(category -> CategoryResponse.builder()
                        .type(category.getName())
                        .build())
                .toList();
    }
}

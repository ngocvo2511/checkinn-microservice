package com.example.hotelservice.Policy.service;

import com.example.hotelservice.Policy.dto.response.PolicyCategoryResponse;
import com.example.hotelservice.Policy.repository.PolicyCategoryTypeRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class PolicyCategoryService {

    private final PolicyCategoryTypeRepository policyCategoryTypeRepository;

    public List<PolicyCategoryResponse> getAvailableCategories() {
        return policyCategoryTypeRepository.findAllSorted()
                .stream()
                .map(category -> PolicyCategoryResponse.builder()
                        .type(category.getName())
                        .build())
                .toList();
    }
}

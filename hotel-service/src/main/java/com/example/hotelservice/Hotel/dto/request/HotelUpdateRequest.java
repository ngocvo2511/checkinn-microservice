package com.example.hotelservice.Hotel.dto.request;

import com.example.hotelservice.Amenity.dto.request.AmenityRequest;
import com.example.hotelservice.Policy.dto.request.PolicyRequest;
import com.example.hotelservice.Question.dto.QuestionRequest;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Pattern;

import java.util.List;
import java.util.UUID;

public record HotelUpdateRequest(
        String name,
        String description,
        Short starRating,
        UUID cityId,
        HotelAddressDto address,
        @Email String contactEmail,
        @Pattern(regexp = "^\\+?[0-9]{7,15}$", message = "Invalid phone number") String contactPhone,
        Boolean isActive,
        List <PolicyRequest> policies,
        List<QuestionRequest> questions,
        List <AmenityRequest> amenityCategories
) {}


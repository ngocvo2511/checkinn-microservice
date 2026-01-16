package com.example.hotelservice.Hotel.service;

import com.example.hotelservice.Amenity.dto.request.AmenityRequest;
import com.example.hotelservice.Hotel.dto.request.HotelCreateRequest;
import com.example.hotelservice.Hotel.dto.request.HotelUpdateRequest;
import com.example.hotelservice.Policy.dto.request.PolicyRequest;
import com.example.hotelservice.Question.dto.QuestionRequest;
import com.example.hotelservice.Hotel.dto.response.HotelResponse;
import com.example.hotelservice.Hotel.dto.response.MyHotelShortResponse;
import com.example.hotelservice.Hotel.dto.response.PendingHotelDetailResponse;
import com.example.hotelservice.Hotel.entity.Hotel;

import java.util.List;
import java.util.UUID;

public interface HotelService {

    Hotel createHotel(HotelCreateRequest request, UUID ownerId);

    Hotel updateHotel(UUID hotelId, HotelUpdateRequest request, UUID ownerId);

    Hotel getById(UUID hotelId);

    com.example.hotelservice.Hotel.dto.response.HotelResponse getDetail(UUID hotelId);

    List<Hotel> getByOwner(UUID ownerId);

    List<Hotel> getByCity(UUID cityId);

    List<Hotel> getByOwnerAndCity(UUID ownerId, UUID cityId);

    List<Hotel> searchByName(String name);

    List<Hotel> getPendingHotels();

    PendingHotelDetailResponse getPendingHotelDetail(UUID hotelId);

    List<Hotel> getAllApprovedHotels();

    void activateHotel(UUID hotelId);

    void deactivateHotel(UUID hotelId);

    void approveHotel(UUID hotelId);

    void rejectHotel(UUID hotelId);

    Hotel updateAmenities(UUID hotelId, List<AmenityRequest> amenityCategories, UUID ownerId);

    Hotel addAmenityCategory(UUID hotelId, AmenityRequest amenityRequest, UUID ownerId);

    Hotel clearAmenities(UUID hotelId, UUID ownerId);

    Hotel removeAmenityCategory(UUID hotelId, String categoryTitle, UUID ownerId);

    Hotel updatePolicies(UUID hotelId, List<PolicyRequest> policies, UUID ownerId);

    Hotel addPolicy(UUID hotelId, PolicyRequest policyRequest, UUID ownerId);

    Hotel clearPolicies(UUID hotelId, UUID ownerId);

    Hotel removePolicy(UUID hotelId, UUID policyId, UUID ownerId);

    Hotel updateQuestions(UUID hotelId, List<QuestionRequest> questions, UUID ownerId);

    Hotel addQuestion(UUID hotelId, QuestionRequest questionRequest, UUID ownerId);

    Hotel clearQuestions(UUID hotelId, UUID ownerId);

    Hotel removeQuestion(UUID hotelId, UUID questionId, UUID ownerId);
}

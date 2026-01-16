package com.example.hotelservice.Hotel.service;

import com.checkinn.user.grpc.UserResponse;
import com.example.hotelservice.Amenity.dto.response.AmenityItemResponse;
import com.example.hotelservice.Amenity.dto.response.AmenityResponse;
import com.example.hotelservice.Amenity.entity.HotelAmenity;
import com.example.hotelservice.Amenity.entity.HotelAmenityCategory;
import com.example.hotelservice.Amenity.repository.HotelAmenityCategoryRepository;
import com.example.hotelservice.Amenity.repository.HotelAmenityRepository;
import com.example.hotelservice.Amenity.dto.request.AmenityRequest;
import com.example.hotelservice.Hotel.dto.request.HotelCreateRequest;
import com.example.hotelservice.Hotel.dto.request.HotelUpdateRequest;
import com.example.hotelservice.Hotel.dto.response.PendingHotelDetailResponse;
import com.example.hotelservice.Hotel.entity.Hotel;
import com.example.hotelservice.Hotel.enums.HotelApprovalStatus;
import com.example.hotelservice.Hotel.mapper.HotelMapper;
import com.example.hotelservice.Hotel.repository.HotelRepository;
import com.example.hotelservice.Policy.dto.response.PolicyResponse;
import com.example.hotelservice.Policy.dto.request.PolicyRequest;
import com.example.hotelservice.Policy.entity.HotelPolicy;
import com.example.hotelservice.Policy.entity.PolicyCategoryType;
import com.example.hotelservice.Policy.repository.HotelPolicyRepository;
import com.example.hotelservice.Policy.repository.PolicyCategoryTypeRepository;
import com.example.hotelservice.Question.dto.QuestionRequest;
import com.example.hotelservice.Question.dto.QuestionResponse;
import com.example.hotelservice.Question.entity.Question;
import com.example.hotelservice.Question.repository.QuestionRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.EntityNotFoundException;
import org.springframework.transaction.annotation.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class HotelServiceImpl implements HotelService {

    private final UserGrpcClient userGrpcClient;
    private final HotelRepository hotelRepository;
    private final HotelPolicyRepository hotelPolicyRepository;
    private final PolicyCategoryTypeRepository policyCategoryTypeRepository;
    private final QuestionRepository questionRepository;
    private final HotelAmenityCategoryRepository hotelAmenityCategoryRepository;
    private final HotelAmenityRepository hotelAmenityRepository;
    private final ObjectMapper objectMapper;
    private final HotelMapper hotelMapper;

    // ========== CREATE HOTEL ==========
    @Override
    @Transactional
    public Hotel createHotel(HotelCreateRequest request, UUID ownerId) {
        Hotel hotel = new Hotel();
        hotel.setOwnerId(ownerId);
        hotel.setCityId(request.cityId());
        hotel.setName(request.name());
        hotel.setDescription(request.description());
        hotel.setStarRating(request.starRating());
        hotel.setAddress(writeJson(request.address()));
        hotel.setContactEmail(request.contactEmail());
        hotel.setContactPhone(request.contactPhone());
        hotel.setBusinessLicenseNumber(request.businessLicenseNumber());
        hotel.setTaxId(request.taxId());
        hotel.setOperationLicenseNumber(request.operationLicenseNumber());
        hotel.setOwnerIdentityNumber(request.ownerIdentityNumber());

        hotel.setIsActive(false);
        hotel.setApprovedStatus(HotelApprovalStatus.PENDING);

        hotel = hotelRepository.save(hotel);
        Hotel finalHotel = hotel;

        if (request.policies() != null) {
            request.policies().forEach(p -> {
                PolicyCategoryType categoryType = policyCategoryTypeRepository
                        .findByName(p.getCategory())
                        .orElseThrow(() -> new EntityNotFoundException("Không tìm thấy loại chính sách: " + p.getCategory()));
                
                HotelPolicy policy = HotelPolicy.builder()
                        .hotel(finalHotel)
                        .category(categoryType)
                        .content(p.getContent())
                        .createdAt(Instant.now())
                        .updatedAt(Instant.now())
                        .build();
                hotelPolicyRepository.save(policy);
            });
        }

        if (request.questions() != null) {
            request.questions().forEach(q -> {
                Question question = Question.builder()
                        .hotel(finalHotel)
                        .question(q.getQuestion())
                        .answer(q.getAnswer())
                        .createdAt(Instant.now())
                        .updatedAt(Instant.now())
                        .build();
                questionRepository.save(question);
            });
        }

        if (request.amenityCategories() != null) {
            request.amenityCategories().forEach(catReq -> {
                HotelAmenityCategory cat = HotelAmenityCategory.builder()
                        .hotelId(finalHotel.getId())
                        .title(catReq.getTitle())
                        .createdAt(Instant.now())
                        .updatedAt(Instant.now())
                        .build();
                cat = hotelAmenityCategoryRepository.save(cat);

                if (catReq.getAmenities() != null) {
                    HotelAmenityCategory finalCat = cat;
                    catReq.getAmenities().forEach(itemReq -> {
                        HotelAmenity amenity = HotelAmenity.builder()
                                .category(finalCat)
                                .title(itemReq.getTitle())
                                .createdAt(Instant.now())
                                .build();
                        hotelAmenityRepository.save(amenity);
                    });
                }
            });
        }
        return hotel;
    }

    // ========== UPDATE HOTEL ==========
    @Override
    @Transactional
    public Hotel updateHotel(UUID hotelId, HotelUpdateRequest request, UUID ownerId) {
        Hotel hotel = getById(hotelId);

        if (!hotel.getOwnerId().equals(ownerId))
            throw new SecurityException("Bạn không có quyền cập nhật khách sạn này.");

        if (request.name() != null) hotel.setName(request.name());
        if (request.description() != null) hotel.setDescription(request.description());
        if (request.starRating() != null) hotel.setStarRating(request.starRating());
        if (request.cityId() != null) hotel.setCityId(request.cityId());
        if (request.address() != null) hotel.setAddress(writeJson(request.address()));
        if (request.contactEmail() != null) hotel.setContactEmail(request.contactEmail());
        if (request.contactPhone() != null) hotel.setContactPhone(request.contactPhone());

        return hotelRepository.save(hotel);
    }

    // ========== GET HOTEL ==========
    @Override
    public Hotel getById(UUID hotelId) {
        return hotelRepository.findById(hotelId)
                .orElseThrow(() -> new EntityNotFoundException("Không tìm thấy khách sạn"));
    }

    @Transactional(readOnly = true)
    @Override
    public com.example.hotelservice.Hotel.dto.response.HotelResponse getDetail(UUID hotelId) {
        Hotel hotel = getById(hotelId);
        return hotelMapper.toHotelResponse(hotel);
    }

    // ========== OWNER: MY HOTELS ==========
    @Override
    public List<Hotel> getByOwner(UUID ownerId) {
        return hotelRepository.findByOwnerId(ownerId);
    }

    // ========== SEARCH: HOTELS BY CITY ==========
    @Override
    public List<Hotel> getByCity(UUID cityId) {
        return hotelRepository.findByCityId(cityId);
    }

    // ========== SEARCH: HOTELS BY NAME ==========
    @Override
    public List<Hotel> searchByName(String name) {
        return hotelRepository.findByNameContainingIgnoreCase(name);
    }

    // ========== SEARCH: HOTELS BY OWNER AND CITY ==========
    @Override
    public List<Hotel> getByOwnerAndCity(UUID ownerId, UUID cityId) {
        return hotelRepository.findByOwnerIdAndCityId(ownerId, cityId);
    }

    // ========== ADMIN: PENDING HOTELS ==========
    @Override
    public List<Hotel> getPendingHotels() {
        return hotelRepository.findByApprovedStatus(HotelApprovalStatus.PENDING);
    }

    @Override
    public List<Hotel> getAllApprovedHotels() {
        return hotelRepository.findByApprovedStatus(HotelApprovalStatus.APPROVED);
    }

    @Override
    public PendingHotelDetailResponse getPendingHotelDetail(UUID hotelId) {
        Hotel hotel = getById(hotelId);
        if (hotel.getApprovedStatus() != HotelApprovalStatus.PENDING) {
            throw new IllegalArgumentException("Khách sạn không ở trạng thái chờ duyệt.");
        }
        UserResponse owner = userGrpcClient.GetUserById(hotel.getOwnerId());
        List<PolicyResponse> policies =
                hotelPolicyRepository.findAllByHotelId(hotelId)
                        .stream()
                        .map(p -> PolicyResponse.builder()
                                .category(p.getCategory().getName())
                                .content(p.getContent())
                                .build())
                        .toList();

        List<QuestionResponse> questions = questionRepository.findAllByHotelId(hotelId)
            .stream()
            .map(q -> QuestionResponse.builder()
                .question(q.getQuestion())
                .answer(q.getAnswer())
                .build())
            .toList();
        List<HotelAmenityCategory> categories =
            hotelAmenityCategoryRepository.findAllByHotelId(hotelId);

        List<HotelAmenity> amenities =
            hotelAmenityRepository.findAllByCategoryHotelId(hotelId);

        Map<UUID, List<HotelAmenity>> amenityMap =
                amenities.stream()
                        .collect(Collectors.groupingBy(
                                a -> a.getCategory().getId()
                        ));

        List<AmenityResponse> amenityResponses =
                categories.stream()
                        .map(category -> AmenityResponse.builder()
                                .id(category.getId().toString())
                                .title(category.getTitle())
                                .items(
                                        amenityMap
                                                .getOrDefault(category.getId(), List.of())
                                                .stream()
                                                .map(item -> AmenityItemResponse.builder()
                                                        .id(item.getId().toString())
                                                        .title(item.getTitle())
                                                        .build())
                                                .toList()
                                )
                                .build()
                        )
                        .toList();


        return hotelMapper.toPendingHotelDetailResponse(hotel, policies, amenityResponses, owner, questions);
    }
    // ========== ADMIN: APPROVE ==========
    @Override @Transactional
    public void approveHotel(UUID hotelId) {
        Hotel hotel = getById(hotelId);
        hotel.setApprovedStatus(HotelApprovalStatus.APPROVED);
        hotel.setIsActive(true);
    }

    // ========== ADMIN: REJECT ==========
    @Override @Transactional
    public void rejectHotel(UUID hotelId) {
        Hotel hotel = getById(hotelId);
        hotel.setApprovedStatus(HotelApprovalStatus.REJECTED);
        hotel.setIsActive(false);
    }

    // ========== ACTIVATE ==========
    @Override @Transactional
    public void activateHotel(UUID hotelId) {
        Hotel hotel = getById(hotelId);
        if(hotel.getApprovedStatus() != HotelApprovalStatus.APPROVED) {
            throw new IllegalArgumentException("Chỉ có thể kích hoạt khách sạn đã được duyệt.");
        }
        hotel.setIsActive(true);
    }

    // ========== ADMIN DISABLE ==========
    @Override @Transactional
    public void deactivateHotel(UUID hotelId) {
        Hotel hotel = getById(hotelId);
        if(hotel.getApprovedStatus() != HotelApprovalStatus.APPROVED) {
            throw new IllegalArgumentException("Chỉ có thể kích hoạt khách sạn đã được duyệt.");
        }
        hotel.setIsActive(false);
    }

    // ========== AMENITIES MANAGEMENT ==========
    @Override
    @Transactional
    public Hotel updateAmenities(UUID hotelId, List<AmenityRequest> amenityCategories, UUID ownerId) {
        Hotel hotel = getById(hotelId);

        if (!hotel.getOwnerId().equals(ownerId))
            throw new SecurityException("Bạn không có quyền cập nhật tiện ích của khách sạn này.");

        hotelAmenityRepository.deleteAllByCategoryHotelId(hotelId);
        hotelAmenityCategoryRepository.deleteAllByHotelId(hotelId);

        // Thêm amenities mới
        if (amenityCategories != null && !amenityCategories.isEmpty()) {
            amenityCategories.forEach(catReq -> {
                HotelAmenityCategory cat = HotelAmenityCategory.builder()
                        .hotelId(hotel.getId())
                        .title(catReq.getTitle())
                        .createdAt(Instant.now())
                        .updatedAt(Instant.now())
                        .build();
                cat = hotelAmenityCategoryRepository.save(cat);

                if (catReq.getAmenities() != null && !catReq.getAmenities().isEmpty()) {
                    HotelAmenityCategory finalCat = cat;
                    catReq.getAmenities().forEach(itemReq -> {
                        HotelAmenity amenity = HotelAmenity.builder()
                                .category(finalCat)
                                .title(itemReq.getTitle())
                                .createdAt(Instant.now())
                                .build();
                        hotelAmenityRepository.save(amenity);
                    });
                }
            });
        }

        return hotel;
    }

    @Override
    @Transactional
    public Hotel addAmenityCategory(UUID hotelId, AmenityRequest amenityRequest, UUID ownerId) {
        Hotel hotel = getById(hotelId);

        if (!hotel.getOwnerId().equals(ownerId))
            throw new SecurityException("Bạn không có quyền thêm tiện ích vào khách sạn này.");

        HotelAmenityCategory cat = HotelAmenityCategory.builder()
                .hotelId(hotel.getId())
                .title(amenityRequest.getTitle())
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();
        cat = hotelAmenityCategoryRepository.save(cat);

        if (amenityRequest.getAmenities() != null && !amenityRequest.getAmenities().isEmpty()) {
            HotelAmenityCategory finalCat = cat;
            amenityRequest.getAmenities().forEach(itemReq -> {
                HotelAmenity amenity = HotelAmenity.builder()
                        .category(finalCat)
                        .title(itemReq.getTitle())
                        .createdAt(Instant.now())
                        .build();
                hotelAmenityRepository.save(amenity);
            });
        }

        return hotel;
    }

    @Override
    @Transactional
    public Hotel clearAmenities(UUID hotelId, UUID ownerId) {
        Hotel hotel = getById(hotelId);

        if (!hotel.getOwnerId().equals(ownerId))
            throw new SecurityException("Bạn không có quyền xóa tiện ích của khách sạn này.");

        hotelAmenityRepository.deleteAllByCategoryHotelId(hotelId);
        hotelAmenityCategoryRepository.deleteAllByHotelId(hotelId);

        return hotel;
    }

    @Override
    @Transactional
    public Hotel removeAmenityCategory(UUID hotelId, String categoryTitle, UUID ownerId) {
        Hotel hotel = getById(hotelId);

        if (!hotel.getOwnerId().equals(ownerId))
            throw new SecurityException("Bạn không có quyền xóa tiện ích của khách sạn này.");

        HotelAmenityCategory category = hotelAmenityCategoryRepository
                .findByHotelIdAndTitle(hotelId, categoryTitle)
                .orElseThrow(() -> new EntityNotFoundException(
                        "Không tìm thấy danh mục tiện ích: " + categoryTitle
                ));

        // Xóa tất cả amenities trong category
        hotelAmenityRepository.deleteAllByCategory(category);
        // Xóa category
        hotelAmenityCategoryRepository.delete(category);

        return hotel;
    }

    // ========== POLICIES MANAGEMENT ==========
    @Override
    @Transactional
    public Hotel updatePolicies(UUID hotelId, List<PolicyRequest> policies, UUID ownerId) {
        Hotel hotel = getById(hotelId);

        if (!hotel.getOwnerId().equals(ownerId))
            throw new SecurityException("Bạn không có quyền cập nhật chính sách của khách sạn này.");

        hotelPolicyRepository.deleteAllByHotelId(hotelId);

        if (policies != null && !policies.isEmpty()) {
            policies.forEach(p -> {
                PolicyCategoryType categoryType = policyCategoryTypeRepository
                        .findByName(p.getCategory())
                        .orElseThrow(() -> new EntityNotFoundException("Không tìm thấy loại chính sách: " + p.getCategory()));
                
                HotelPolicy policy = HotelPolicy.builder()
                        .hotel(hotel)
                        .category(categoryType)
                        .content(p.getContent())
                        .createdAt(Instant.now())
                        .updatedAt(Instant.now())
                        .build();
                hotelPolicyRepository.save(policy);
            });
        }

        return hotel;
    }

    @Override
    @Transactional
    public Hotel addPolicy(UUID hotelId, PolicyRequest policyRequest, UUID ownerId) {
        Hotel hotel = getById(hotelId);

        if (!hotel.getOwnerId().equals(ownerId))
            throw new SecurityException("Bạn không có quyền thêm chính sách cho khách sạn này.");

        PolicyCategoryType categoryType = policyCategoryTypeRepository
                .findByName(policyRequest.getCategory())
                .orElseThrow(() -> new EntityNotFoundException("Không tìm thấy loại chính sách: " + policyRequest.getCategory()));

        HotelPolicy policy = HotelPolicy.builder()
                .hotel(hotel)
                .category(categoryType)
                .content(policyRequest.getContent())
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();
        hotelPolicyRepository.save(policy);

        return hotel;
    }

    @Override
    @Transactional
    public Hotel clearPolicies(UUID hotelId, UUID ownerId) {
        Hotel hotel = getById(hotelId);

        if (!hotel.getOwnerId().equals(ownerId))
            throw new SecurityException("Bạn không có quyền xóa chính sách của khách sạn này.");

        hotelPolicyRepository.deleteAllByHotelId(hotelId);

        return hotel;
    }

    @Override
    @Transactional
    public Hotel removePolicy(UUID hotelId, UUID policyId, UUID ownerId) {
        Hotel hotel = getById(hotelId);

        if (!hotel.getOwnerId().equals(ownerId))
            throw new SecurityException("Bạn không có quyền xóa chính sách của khách sạn này.");

        HotelPolicy policy = hotelPolicyRepository
                .findByIdAndHotelId(policyId, hotelId)
                .orElseThrow(() -> new EntityNotFoundException("Không tìm thấy chính sách"));

        hotelPolicyRepository.delete(policy);

        return hotel;
    }

    // ========== FAQs (Questions) MANAGEMENT ==========
    @Override
    @Transactional
    public Hotel updateQuestions(UUID hotelId, List<QuestionRequest> questions, UUID ownerId) {
        Hotel hotel = getById(hotelId);

        if (!hotel.getOwnerId().equals(ownerId))
            throw new SecurityException("Bạn không có quyền cập nhật câu hỏi của khách sạn này.");

        questionRepository.deleteAllByHotelId(hotelId);

        if (questions != null && !questions.isEmpty()) {
            questions.forEach(q -> {
                Question question = Question.builder()
                        .hotel(hotel)
                        .question(q.getQuestion())
                        .answer(q.getAnswer())
                        .createdAt(Instant.now())
                        .updatedAt(Instant.now())
                        .build();
                questionRepository.save(question);
            });
        }

        return hotel;
    }

    @Override
    @Transactional
    public Hotel addQuestion(UUID hotelId, QuestionRequest questionRequest, UUID ownerId) {
        Hotel hotel = getById(hotelId);

        if (!hotel.getOwnerId().equals(ownerId))
            throw new SecurityException("Bạn không có quyền thêm câu hỏi cho khách sạn này.");

        Question question = Question.builder()
                .hotel(hotel)
                .question(questionRequest.getQuestion())
                .answer(questionRequest.getAnswer())
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();
        questionRepository.save(question);

        return hotel;
    }

    @Override
    @Transactional
    public Hotel clearQuestions(UUID hotelId, UUID ownerId) {
        Hotel hotel = getById(hotelId);

        if (!hotel.getOwnerId().equals(ownerId))
            throw new SecurityException("Bạn không có quyền xóa câu hỏi của khách sạn này.");

        questionRepository.deleteAllByHotelId(hotelId);

        return hotel;
    }

    @Override
    @Transactional
    public Hotel removeQuestion(UUID hotelId, UUID questionId, UUID ownerId) {
        Hotel hotel = getById(hotelId);

        if (!hotel.getOwnerId().equals(ownerId))
            throw new SecurityException("Bạn không có quyền xóa câu hỏi của khách sạn này.");

        Question question = questionRepository
                .findByIdAndHotelId(questionId, hotelId)
                .orElseThrow(() -> new EntityNotFoundException("Không tìm thấy câu hỏi"));

        questionRepository.delete(question);

        return hotel;
    }

    private String writeJson(Object obj) {
        try {
            return objectMapper.writeValueAsString(obj);
        } catch (Exception ex) {
            throw new RuntimeException(ex);
        }
    }
}

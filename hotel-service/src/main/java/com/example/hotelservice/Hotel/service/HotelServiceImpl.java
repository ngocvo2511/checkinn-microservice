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
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

import com.example.hotelservice.MediaAsset.service.MediaClient;
import org.springframework.web.multipart.MultipartFile;

@Service
@RequiredArgsConstructor
@Slf4j
public class HotelServiceImpl implements HotelService {

    private final UserGrpcClient userGrpcClient;
    private final MediaClient mediaClient;
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
        log.info("[CREATE_HOTEL] Creating hotel - ownerId: {}, cityId: {}, name: {}", ownerId, request.cityId(), request.name());
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
    log.info("[CREATE_HOTEL] Hotel created successfully - hotelId: {}, ownerId: {}, name: {}", hotel.getId(), ownerId, hotel.getName());
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
        log.info("[UPDATE_HOTEL] Updating hotel - hotelId: {}, ownerId: {}", hotelId, ownerId);
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
        if (request.businessLicenseNumber() != null) hotel.setBusinessLicenseNumber(request.businessLicenseNumber());
        if (request.taxId() != null) hotel.setTaxId(request.taxId());
        if (request.operationLicenseNumber() != null) hotel.setOperationLicenseNumber(request.operationLicenseNumber());
        if (request.ownerIdentityNumber() != null) hotel.setOwnerIdentityNumber(request.ownerIdentityNumber());
        Hotel saved = hotelRepository.save(hotel);
        log.info("[UPDATE_HOTEL] Hotel updated successfully - hotelId: {}, ownerId: {}", hotelId, ownerId);
        return saved;
    }

    // ========== GET HOTEL ==========
    @Override
    public Hotel getById(UUID hotelId) {
        log.info("[GET_HOTEL] Fetching hotel - hotelId: {}", hotelId);
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
        log.info("[GET_HOTELS_BY_OWNER] Fetching hotels for ownerId: {}", ownerId);
        return hotelRepository.findByOwnerId(ownerId);
    }

    // ========== SEARCH: HOTELS BY CITY ==========
    @Override
    public List<Hotel> getByCity(UUID cityId) {
        log.info("[GET_HOTELS_BY_CITY] Fetching hotels for cityId: {}", cityId);
        return hotelRepository.findByCityId(cityId);
    }

    // ========== SEARCH: HOTELS BY NAME ==========
    @Override
    public List<Hotel> searchByName(String name) {
        log.info("[SEARCH_HOTELS] Searching hotels by name: {}", name);
        return hotelRepository.findByNameContainingIgnoreCase(name);
    }

    // ========== SEARCH: HOTELS BY OWNER AND CITY ==========
    @Override
    public List<Hotel> getByOwnerAndCity(UUID ownerId, UUID cityId) {
        log.info("[GET_HOTELS_BY_OWNER_AND_CITY] Fetching hotels - ownerId: {}, cityId: {}", ownerId, cityId);
        return hotelRepository.findByOwnerIdAndCityId(ownerId, cityId);
    }

    // ========== ADMIN: PENDING HOTELS ==========
    @Override
    public List<Hotel> getPendingHotels() {
        log.info("[GET_PENDING_HOTELS] Fetching pending hotels");
        return hotelRepository.findByApprovedStatus(HotelApprovalStatus.PENDING);
    }

    @Override
    public List<Hotel> getAllApprovedHotels() {
        log.info("[GET_APPROVED_HOTELS] Fetching approved hotels");
        return hotelRepository.findByApprovedStatus(HotelApprovalStatus.APPROVED);
    }

    @Override
    public PendingHotelDetailResponse getPendingHotelDetail(UUID hotelId) {
        log.info("[GET_PENDING_HOTEL_DETAIL] Fetching pending hotel detail - hotelId: {}", hotelId);
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

        log.info("[GET_PENDING_HOTEL_DETAIL] Pending hotel detail loaded successfully - hotelId: {}", hotelId);
        var response = hotelMapper.toPendingHotelDetailResponse(hotel, policies, amenityResponses, owner, questions);
        response.setRejectionReason(hotel.getRejectionReason());

        if (hotel.getBusinessLicenseFilePath() != null && !hotel.getBusinessLicenseFilePath().isBlank()) {
            try {
                response.setBusinessLicenseUrl(mediaClient.getSignedUrl(hotel.getBusinessLicenseFilePath(), 300));
            } catch (Exception e) {
                log.warn("[GET_PENDING_HOTEL_DETAIL] Failed to generate signed url for businessLicense: {}", e.getMessage());
            }
        }
        if (hotel.getOperationLicenseFilePath() != null && !hotel.getOperationLicenseFilePath().isBlank()) {
            try {
                response.setOperationLicenseUrl(mediaClient.getSignedUrl(hotel.getOperationLicenseFilePath(), 300));
            } catch (Exception e) {
                log.warn("[GET_PENDING_HOTEL_DETAIL] Failed to generate signed url for operationLicense: {}", e.getMessage());
            }
        }
        if (hotel.getOwnerIdentityFrontPath() != null && !hotel.getOwnerIdentityFrontPath().isBlank()) {
            try {
                response.setOwnerIdentityFrontUrl(mediaClient.getSignedUrl(hotel.getOwnerIdentityFrontPath(), 300));
            } catch (Exception e) {
                log.warn("[GET_PENDING_HOTEL_DETAIL] Failed to generate signed url for ownerIdentityFront: {}", e.getMessage());
            }
        }
        if (hotel.getOwnerIdentityBackPath() != null && !hotel.getOwnerIdentityBackPath().isBlank()) {
            try {
                response.setOwnerIdentityBackUrl(mediaClient.getSignedUrl(hotel.getOwnerIdentityBackPath(), 300));
            } catch (Exception e) {
                log.warn("[GET_PENDING_HOTEL_DETAIL] Failed to generate signed url for ownerIdentityBack: {}", e.getMessage());
            }
        }

        return response;
    }

    // ========== UPLOAD DOCUMENTS ==========
    @Override
    @Transactional
    public Hotel uploadHotelDocuments(
            UUID hotelId,
            UUID ownerId,
            MultipartFile businessLicense,
            MultipartFile operationLicense,
            MultipartFile ownerIdentityFront,
            MultipartFile ownerIdentityBack
    ) {
        log.info("[UPLOAD_HOTEL_DOCUMENTS] Uploading documents - hotelId: {}, ownerId: {}", hotelId, ownerId);
        Hotel hotel = getById(hotelId);
        if (!hotel.getOwnerId().equals(ownerId)) {
            throw new org.springframework.security.access.AccessDeniedException("Bạn không có quyền cập nhật giấy tờ của khách sạn này.");
        }

        try {
            if (businessLicense != null && !businessLicense.isEmpty()) {
                String ext = getFileExtension(businessLicense.getOriginalFilename());
                String fileName = "license_" + hotelId + ext;
                String mimeType = businessLicense.getContentType() != null ? businessLicense.getContentType() : "image/jpeg";
                String path = mediaClient.uploadPrivateDocument(fileName, mimeType, businessLicense.getBytes());
                hotel.setBusinessLicenseFilePath(path);
            }

            if (operationLicense != null && !operationLicense.isEmpty()) {
                String ext = getFileExtension(operationLicense.getOriginalFilename());
                String fileName = "operation_" + hotelId + ext;
                String mimeType = operationLicense.getContentType() != null ? operationLicense.getContentType() : "image/jpeg";
                String path = mediaClient.uploadPrivateDocument(fileName, mimeType, operationLicense.getBytes());
                hotel.setOperationLicenseFilePath(path);
            }

            if (ownerIdentityFront != null && !ownerIdentityFront.isEmpty()) {
                String ext = getFileExtension(ownerIdentityFront.getOriginalFilename());
                String fileName = "id_front_" + hotelId + ext;
                String mimeType = ownerIdentityFront.getContentType() != null ? ownerIdentityFront.getContentType() : "image/jpeg";
                String path = mediaClient.uploadPrivateDocument(fileName, mimeType, ownerIdentityFront.getBytes());
                hotel.setOwnerIdentityFrontPath(path);
            }

            if (ownerIdentityBack != null && !ownerIdentityBack.isEmpty()) {
                String ext = getFileExtension(ownerIdentityBack.getOriginalFilename());
                String fileName = "id_back_" + hotelId + ext;
                String mimeType = ownerIdentityBack.getContentType() != null ? ownerIdentityBack.getContentType() : "image/jpeg";
                String path = mediaClient.uploadPrivateDocument(fileName, mimeType, ownerIdentityBack.getBytes());
                hotel.setOwnerIdentityBackPath(path);
            }

            if (hotel.getApprovedStatus() == HotelApprovalStatus.REJECTED) {
                hotel.setApprovedStatus(HotelApprovalStatus.PENDING);
                log.info("[UPLOAD_HOTEL_DOCUMENTS] Hotel was REJECTED, reset status to PENDING for re-review - hotelId: {}", hotelId);
            }

            hotel = hotelRepository.save(hotel);
            log.info("[UPLOAD_HOTEL_DOCUMENTS] Documents uploaded successfully - hotelId: {}", hotelId);
            return hotel;
        } catch (Exception e) {
            log.error("[UPLOAD_HOTEL_DOCUMENTS] Failed to upload documents - hotelId: {}, error: {}", hotelId, e.getMessage(), e);
            throw new RuntimeException("Lỗi khi tải tài liệu xác thực: " + e.getMessage(), e);
        }
    }

    @Override
    public com.example.hotelservice.Hotel.dto.response.HotelVerificationDocumentsResponse getVerificationDocuments(
            UUID hotelId, UUID requesterId, boolean isAdmin
    ) {
        Hotel hotel = getById(hotelId);
        if (!isAdmin && !hotel.getOwnerId().equals(requesterId)) {
            throw new org.springframework.security.access.AccessDeniedException("Bạn không có quyền xem tài liệu của khách sạn này.");
        }

        String businessLicenseUrl = null;
        String operationLicenseUrl = null;
        String ownerIdentityFrontUrl = null;
        String ownerIdentityBackUrl = null;

        if (hotel.getBusinessLicenseFilePath() != null && !hotel.getBusinessLicenseFilePath().isBlank()) {
            try {
                businessLicenseUrl = mediaClient.getSignedUrl(hotel.getBusinessLicenseFilePath(), 300);
            } catch (Exception e) {
                log.warn("[GET_VERIFICATION_DOCS] Failed to generate signed url for businessLicense: {}", e.getMessage());
            }
        }
        if (hotel.getOperationLicenseFilePath() != null && !hotel.getOperationLicenseFilePath().isBlank()) {
            try {
                operationLicenseUrl = mediaClient.getSignedUrl(hotel.getOperationLicenseFilePath(), 300);
            } catch (Exception e) {
                log.warn("[GET_VERIFICATION_DOCS] Failed to generate signed url for operationLicense: {}", e.getMessage());
            }
        }
        if (hotel.getOwnerIdentityFrontPath() != null && !hotel.getOwnerIdentityFrontPath().isBlank()) {
            try {
                ownerIdentityFrontUrl = mediaClient.getSignedUrl(hotel.getOwnerIdentityFrontPath(), 300);
            } catch (Exception e) {
                log.warn("[GET_VERIFICATION_DOCS] Failed to generate signed url for ownerIdentityFront: {}", e.getMessage());
            }
        }
        if (hotel.getOwnerIdentityBackPath() != null && !hotel.getOwnerIdentityBackPath().isBlank()) {
            try {
                ownerIdentityBackUrl = mediaClient.getSignedUrl(hotel.getOwnerIdentityBackPath(), 300);
            } catch (Exception e) {
                log.warn("[GET_VERIFICATION_DOCS] Failed to generate signed url for ownerIdentityBack: {}", e.getMessage());
            }
        }

        return com.example.hotelservice.Hotel.dto.response.HotelVerificationDocumentsResponse.builder()
                .hotelId(hotel.getId())
                .approvedStatus(hotel.getApprovedStatus())
                .rejectionReason(hotel.getRejectionReason())
                .businessLicenseNumber(hotel.getBusinessLicenseNumber())
                .taxId(hotel.getTaxId())
                .operationLicenseNumber(hotel.getOperationLicenseNumber())
                .ownerIdentityNumber(hotel.getOwnerIdentityNumber())
                .businessLicenseUrl(businessLicenseUrl)
                .operationLicenseUrl(operationLicenseUrl)
                .ownerIdentityFrontUrl(ownerIdentityFrontUrl)
                .ownerIdentityBackUrl(ownerIdentityBackUrl)
                .build();
    }

    // ========== ADMIN: APPROVE ==========
    @Override @Transactional
    public void approveHotel(UUID hotelId) {
        log.info("[APPROVE_HOTEL] Approving hotel - hotelId: {}", hotelId);
        Hotel hotel = getById(hotelId);
        hotel.setApprovedStatus(HotelApprovalStatus.APPROVED);
        hotel.setRejectionReason(null);
        hotel.setIsActive(true);
        log.info("[APPROVE_HOTEL] Hotel approved successfully - hotelId: {}", hotelId);
    }

    // ========== ADMIN: REJECT ==========
    @Override @Transactional
    public void rejectHotel(UUID hotelId, String note) {
        log.info("[REJECT_HOTEL] Rejecting hotel - hotelId: {}, note: {}", hotelId, note);
        Hotel hotel = getById(hotelId);
        hotel.setApprovedStatus(HotelApprovalStatus.REJECTED);
        hotel.setRejectionReason(note);
        hotel.setIsActive(false);
        log.info("[REJECT_HOTEL] Hotel rejected successfully - hotelId: {}", hotelId);
    }

    // ========== ACTIVATE ==========
    @Override @Transactional
    public void activateHotel(UUID hotelId) {
        log.info("[ACTIVATE_HOTEL] Activating hotel - hotelId: {}", hotelId);
        Hotel hotel = getById(hotelId);
        if(hotel.getApprovedStatus() != HotelApprovalStatus.APPROVED) {
            throw new IllegalArgumentException("Chỉ có thể kích hoạt khách sạn đã được duyệt.");
        }
        hotel.setIsActive(true);
        log.info("[ACTIVATE_HOTEL] Hotel activated successfully - hotelId: {}", hotelId);
    }

    // ========== ADMIN DISABLE ==========
    @Override @Transactional
    public void deactivateHotel(UUID hotelId) {
        log.info("[DEACTIVATE_HOTEL] Deactivating hotel - hotelId: {}", hotelId);
        Hotel hotel = getById(hotelId);
        if(hotel.getApprovedStatus() != HotelApprovalStatus.APPROVED) {
            throw new IllegalArgumentException("Chỉ có thể kích hoạt khách sạn đã được duyệt.");
        }
        hotel.setIsActive(false);
        log.info("[DEACTIVATE_HOTEL] Hotel deactivated successfully - hotelId: {}", hotelId);
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

    private String getFileExtension(String originalFilename) {
        if (originalFilename != null && originalFilename.contains(".")) {
            String ext = originalFilename.substring(originalFilename.lastIndexOf(".")).toLowerCase();
            ext = ext.replaceAll("[^a-z0-9.]", "");
            if (!ext.isBlank()) {
                return ext;
            }
        }
        return ".jpg";
    }
}

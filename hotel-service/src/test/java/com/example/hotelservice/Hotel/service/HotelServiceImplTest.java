package com.example.hotelservice.Hotel.service;

import com.checkinn.user.grpc.UserResponse;
import com.example.hotelservice.Amenity.repository.HotelAmenityCategoryRepository;
import com.example.hotelservice.Amenity.repository.HotelAmenityRepository;
import com.example.hotelservice.City.entity.City;
import com.example.hotelservice.City.repository.CityRepository;
import com.example.hotelservice.Hotel.dto.request.HotelAddressDto;
import com.example.hotelservice.Hotel.dto.request.HotelCreateRequest;
import com.example.hotelservice.Hotel.dto.request.HotelUpdateRequest;
import com.example.hotelservice.Hotel.dto.response.HotelVerificationDocumentsResponse;
import com.example.hotelservice.Hotel.dto.response.PendingHotelDetailResponse;
import com.example.hotelservice.Hotel.entity.Hotel;
import com.example.hotelservice.Hotel.enums.HotelApprovalStatus;
import com.example.hotelservice.Hotel.mapper.HotelMapper;
import com.example.hotelservice.Hotel.repository.HotelRepository;
import com.example.hotelservice.MediaAsset.service.MediaClient;
import com.example.hotelservice.MediaAsset.repository.MediaAssetRepository;
import com.example.hotelservice.MediaAsset.entity.MediaAsset;
import com.example.hotelservice.MediaAsset.enums.MediaTargetType;
import org.springframework.security.access.AccessDeniedException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import com.example.hotelservice.Policy.repository.HotelPolicyRepository;
import com.example.hotelservice.Policy.repository.PolicyCategoryTypeRepository;
import com.example.hotelservice.Question.repository.QuestionRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class HotelServiceImplTest {

    private UserGrpcClient userGrpcClient;
    private MediaClient mediaClient;
    private HotelRepository hotelRepository;
    private HotelPolicyRepository hotelPolicyRepository;
    private PolicyCategoryTypeRepository policyCategoryTypeRepository;
    private QuestionRepository questionRepository;
    private HotelAmenityCategoryRepository hotelAmenityCategoryRepository;
    private HotelAmenityRepository hotelAmenityRepository;
    private CityRepository cityRepository;
    private ObjectMapper objectMapper;
    private HotelMapper hotelMapper;
    private MediaAssetRepository mediaAssetRepository;

    private HotelServiceImpl hotelService;

    @BeforeEach
    void setUp() {
        userGrpcClient = mock(UserGrpcClient.class);
        mediaClient = mock(MediaClient.class);
        hotelRepository = mock(HotelRepository.class);
        hotelPolicyRepository = mock(HotelPolicyRepository.class);
        policyCategoryTypeRepository = mock(PolicyCategoryTypeRepository.class);
        questionRepository = mock(QuestionRepository.class);
        hotelAmenityCategoryRepository = mock(HotelAmenityCategoryRepository.class);
        hotelAmenityRepository = mock(HotelAmenityRepository.class);
        cityRepository = mock(CityRepository.class);
        objectMapper = mock(ObjectMapper.class);
        hotelMapper = mock(HotelMapper.class);
        mediaAssetRepository = mock(MediaAssetRepository.class);

        hotelService = new HotelServiceImpl(
                userGrpcClient,
                mediaClient,
                hotelRepository,
                hotelPolicyRepository,
                policyCategoryTypeRepository,
                questionRepository,
                hotelAmenityCategoryRepository,
                hotelAmenityRepository,
                cityRepository,
                objectMapper,
                hotelMapper,
                mediaAssetRepository
        );
    }

    @Test
    void getVerificationDocuments_WhenHotelHasNoDocuments_ShouldNotThrowNpeAndReturnNullUrls() {
        UUID hotelId = UUID.randomUUID();
        UUID ownerId = UUID.randomUUID();

        Hotel hotel = new Hotel();
        hotel.setId(hotelId);
        hotel.setOwnerId(ownerId);
        hotel.setApprovedStatus(HotelApprovalStatus.PENDING);
        hotel.setBusinessLicenseFilePath(null);
        hotel.setOperationLicenseFilePath(null);
        hotel.setOwnerIdentityFrontPath(null);
        hotel.setOwnerIdentityBackPath(null);

        when(hotelRepository.findById(hotelId)).thenReturn(Optional.of(hotel));

        HotelVerificationDocumentsResponse response = hotelService.getVerificationDocuments(hotelId, ownerId, false);

        assertThat(response).isNotNull();
        assertThat(response.businessLicenseUrl()).isNull();
        assertThat(response.operationLicenseUrl()).isNull();
        assertThat(response.ownerIdentityFrontUrl()).isNull();
        assertThat(response.ownerIdentityBackUrl()).isNull();

        verify(mediaClient, never()).getSignedUrls(anyList(), anyInt());
    }

    @Test
    void getVerificationDocuments_WhenHotelHasPartialDocuments_ShouldResolveExistingOnly() {
        UUID hotelId = UUID.randomUUID();
        UUID ownerId = UUID.randomUUID();

        Hotel hotel = new Hotel();
        hotel.setId(hotelId);
        hotel.setOwnerId(ownerId);
        hotel.setApprovedStatus(HotelApprovalStatus.PENDING);
        hotel.setBusinessLicenseFilePath("docs/biz.pdf");
        hotel.setOperationLicenseFilePath(null);
        hotel.setOwnerIdentityFrontPath("");
        hotel.setOwnerIdentityBackPath(null);

        when(hotelRepository.findById(hotelId)).thenReturn(Optional.of(hotel));
        when(mediaClient.getSignedUrls(List.of("docs/biz.pdf"), 300))
                .thenReturn(Map.of("docs/biz.pdf", "https://signed.url/biz.pdf"));

        HotelVerificationDocumentsResponse response = hotelService.getVerificationDocuments(hotelId, ownerId, false);

        assertThat(response).isNotNull();
        assertThat(response.businessLicenseUrl()).isEqualTo("https://signed.url/biz.pdf");
        assertThat(response.operationLicenseUrl()).isNull();
        assertThat(response.ownerIdentityFrontUrl()).isNull();
        assertThat(response.ownerIdentityBackUrl()).isNull();
    }

    @Test
    void getPendingHotelDetail_WhenHotelHasNoDocuments_ShouldNotThrowNpe() {
        UUID hotelId = UUID.randomUUID();
        UUID ownerId = UUID.randomUUID();

        Hotel hotel = new Hotel();
        hotel.setId(hotelId);
        hotel.setOwnerId(ownerId);
        hotel.setApprovedStatus(HotelApprovalStatus.PENDING);
        hotel.setBusinessLicenseFilePath(null);
        hotel.setOperationLicenseFilePath(null);
        hotel.setOwnerIdentityFrontPath(null);
        hotel.setOwnerIdentityBackPath(null);

        when(hotelRepository.findById(hotelId)).thenReturn(Optional.of(hotel));
        when(userGrpcClient.GetUserById(ownerId)).thenReturn(UserResponse.newBuilder().build());
        when(hotelPolicyRepository.findAllByHotelId(hotelId)).thenReturn(Collections.emptyList());
        when(questionRepository.findAllByHotelId(hotelId)).thenReturn(Collections.emptyList());
        when(hotelAmenityCategoryRepository.findAllByHotelId(hotelId)).thenReturn(Collections.emptyList());
        when(hotelAmenityRepository.findAllByCategoryHotelId(hotelId)).thenReturn(Collections.emptyList());

        PendingHotelDetailResponse mapperResponse = PendingHotelDetailResponse.builder().build();
        when(hotelMapper.toPendingHotelDetailResponse(any(), any(), any(), any(), any())).thenReturn(mapperResponse);

        PendingHotelDetailResponse response = hotelService.getPendingHotelDetail(hotelId);

        assertThat(response).isNotNull();
        assertThat(response.getBusinessLicenseUrl()).isNull();
        assertThat(response.getOperationLicenseUrl()).isNull();
        assertThat(response.getOwnerIdentityFrontUrl()).isNull();
        assertThat(response.getOwnerIdentityBackUrl()).isNull();
    }

    @Test
    void createHotel_WhenCoordinatesMissing_ShouldFallbackToCityCoordinates() throws Exception {
        UUID ownerId = UUID.randomUUID();
        UUID cityId = UUID.randomUUID();

        City city = City.builder()
                .id(cityId)
                .name("Da Nang")
                .latitude(16.0544)
                .longitude(108.2022)
                .build();
        when(cityRepository.findById(cityId)).thenReturn(Optional.of(city));

        HotelAddressDto inputAddress = new HotelAddressDto("123 Street", "Da Nang", "Vietnam", null, null);
        HotelCreateRequest request = new HotelCreateRequest(
                "My Hotel",
                "Description",
                (short) 4,
                cityId,
                inputAddress,
                "email@test.com",
                "+84123456789",
                "BIZ123",
                "TAX123",
                "OP123",
                "012345678901",
                null,
                null,
                null
        );

        when(hotelRepository.save(any(Hotel.class))).thenAnswer(invocation -> {
            Hotel h = invocation.getArgument(0);
            h.setId(UUID.randomUUID());
            return h;
        });

        Hotel created = hotelService.createHotel(request, ownerId);

        assertThat(created).isNotNull();
        assertThat(created.getApprovedStatus()).isEqualTo(HotelApprovalStatus.DRAFT);
        verify(cityRepository).findById(cityId);
        verify(objectMapper).writeValueAsString(org.mockito.ArgumentMatchers.argThat(obj -> {
            if (obj instanceof HotelAddressDto addr) {
                return addr.latitude() != null && addr.latitude().equals(16.0544)
                        && addr.longitude() != null && addr.longitude().equals(108.2022);
            }
            return false;
        }));
    }

    @Test
    void updateHotel_WhenCoordinatesProvided_ShouldPreserveCoordinates() throws Exception {
        UUID ownerId = UUID.randomUUID();
        UUID hotelId = UUID.randomUUID();
        UUID cityId = UUID.randomUUID();

        Hotel existingHotel = new Hotel();
        existingHotel.setId(hotelId);
        existingHotel.setOwnerId(ownerId);
        existingHotel.setCityId(cityId);

        when(hotelRepository.findById(hotelId)).thenReturn(Optional.of(existingHotel));
        when(hotelRepository.save(any(Hotel.class))).thenAnswer(invocation -> invocation.getArgument(0));

        HotelAddressDto updatedAddress = new HotelAddressDto("456 New St", "Da Nang", "Vietnam", 16.08, 108.22);
        HotelUpdateRequest updateRequest = new HotelUpdateRequest(
                "Updated Name",
                null,
                null,
                null,
                updatedAddress,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null
        );

        Hotel updated = hotelService.updateHotel(hotelId, updateRequest, ownerId);

        assertThat(updated).isNotNull();
        verify(cityRepository, never()).findById(any());
        verify(objectMapper).writeValueAsString(org.mockito.ArgumentMatchers.argThat(obj -> {
            if (obj instanceof HotelAddressDto addr) {
                return addr.latitude().equals(16.08) && addr.longitude().equals(108.22);
            }
            return false;
        }));
    }

    @Test
    void createHotel_WhenCoordinatesZero_ShouldFallbackToCityCoordinates() throws Exception {
        UUID ownerId = UUID.randomUUID();
        UUID cityId = UUID.randomUUID();

        City city = City.builder()
                .id(cityId)
                .name("Da Nang")
                .latitude(16.0544)
                .longitude(108.2022)
                .build();
        when(cityRepository.findById(cityId)).thenReturn(Optional.of(city));

        HotelAddressDto zeroAddress = new HotelAddressDto("123 Street", "Da Nang", "Vietnam", 0.0, 0.0);
        HotelCreateRequest request = new HotelCreateRequest(
                "Zero Coord Hotel",
                "Description",
                (short) 4,
                cityId,
                zeroAddress,
                "email@test.com",
                "+84123456789",
                "BIZ123",
                "TAX123",
                "OP123",
                "012345678901",
                null,
                null,
                null
        );

        when(hotelRepository.save(any(Hotel.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Hotel created = hotelService.createHotel(request, ownerId);

        assertThat(created).isNotNull();
        assertThat(created.getApprovedStatus()).isEqualTo(HotelApprovalStatus.DRAFT);
        verify(cityRepository).findById(cityId);
        verify(objectMapper).writeValueAsString(org.mockito.ArgumentMatchers.argThat(obj -> {
            if (obj instanceof HotelAddressDto addr) {
                return addr.latitude() != null && addr.latitude().equals(16.0544)
                        && addr.longitude() != null && addr.longitude().equals(108.2022);
            }
            return false;
        }));
    }

    @Test
    void createHotel_WhenCoordinatesProvided_ShouldPreserveCoordinates() throws Exception {
        UUID ownerId = UUID.randomUUID();
        UUID cityId = UUID.randomUUID();

        HotelAddressDto validAddress = new HotelAddressDto("123 Street", "Da Nang", "Vietnam", 16.0712, 108.2134);
        HotelCreateRequest request = new HotelCreateRequest(
                "Valid Coord Hotel",
                "Description",
                (short) 4,
                cityId,
                validAddress,
                "email@test.com",
                "+84123456789",
                "BIZ123",
                "TAX123",
                "OP123",
                "012345678901",
                null,
                null,
                null
        );

        when(hotelRepository.save(any(Hotel.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Hotel created = hotelService.createHotel(request, ownerId);

        assertThat(created).isNotNull();
        assertThat(created.getApprovedStatus()).isEqualTo(HotelApprovalStatus.DRAFT);
        verify(cityRepository, never()).findById(any());
        verify(objectMapper).writeValueAsString(org.mockito.ArgumentMatchers.argThat(obj -> {
            if (obj instanceof HotelAddressDto addr) {
                return addr.latitude().equals(16.0712) && addr.longitude().equals(108.2134);
            }
            return false;
        }));
    }

    @Test
    void submitForApproval_Success() {
        UUID ownerId = UUID.randomUUID();
        UUID hotelId = UUID.randomUUID();

        Hotel hotel = new Hotel();
        hotel.setId(hotelId);
        hotel.setOwnerId(ownerId);
        hotel.setApprovedStatus(HotelApprovalStatus.DRAFT);
        hotel.setBusinessLicenseFilePath("docs/biz.pdf");
        hotel.setOwnerIdentityFrontPath("docs/front.jpg");
        hotel.setOwnerIdentityBackPath("docs/back.jpg");

        when(hotelRepository.findById(hotelId)).thenReturn(Optional.of(hotel));
        when(mediaAssetRepository.findFirstByTargetIdAndTargetTypeAndIsThumbnailTrue(hotelId, MediaTargetType.HOTEL))
                .thenReturn(new MediaAsset());
        when(hotelRepository.save(any(Hotel.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Hotel result = hotelService.submitForApproval(hotelId, ownerId);

        assertThat(result.getApprovedStatus()).isEqualTo(HotelApprovalStatus.PENDING);
        verify(hotelRepository).save(hotel);
    }

    @Test
    void submitForApproval_WhenNotOwner_ShouldThrowAccessDenied() {
        UUID ownerId = UUID.randomUUID();
        UUID otherId = UUID.randomUUID();
        UUID hotelId = UUID.randomUUID();

        Hotel hotel = new Hotel();
        hotel.setId(hotelId);
        hotel.setOwnerId(ownerId);

        when(hotelRepository.findById(hotelId)).thenReturn(Optional.of(hotel));

        assertThatThrownBy(() -> hotelService.submitForApproval(hotelId, otherId))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void submitForApproval_WhenMissingDocuments_ShouldThrowIllegalArgument() {
        UUID ownerId = UUID.randomUUID();
        UUID hotelId = UUID.randomUUID();

        Hotel hotel = new Hotel();
        hotel.setId(hotelId);
        hotel.setOwnerId(ownerId);
        hotel.setApprovedStatus(HotelApprovalStatus.DRAFT);
        hotel.setBusinessLicenseFilePath(null);

        when(hotelRepository.findById(hotelId)).thenReturn(Optional.of(hotel));

        assertThatThrownBy(() -> hotelService.submitForApproval(hotelId, ownerId))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Vui lòng tải lên đầy đủ giấy tờ xác thực");
    }

    @Test
    void submitForApproval_WhenMissingThumbnail_ShouldThrowIllegalArgument() {
        UUID ownerId = UUID.randomUUID();
        UUID hotelId = UUID.randomUUID();

        Hotel hotel = new Hotel();
        hotel.setId(hotelId);
        hotel.setOwnerId(ownerId);
        hotel.setApprovedStatus(HotelApprovalStatus.DRAFT);
        hotel.setBusinessLicenseFilePath("docs/biz.pdf");
        hotel.setOwnerIdentityFrontPath("docs/front.jpg");
        hotel.setOwnerIdentityBackPath("docs/back.jpg");

        when(hotelRepository.findById(hotelId)).thenReturn(Optional.of(hotel));
        when(mediaAssetRepository.findFirstByTargetIdAndTargetTypeAndIsThumbnailTrue(hotelId, MediaTargetType.HOTEL))
                .thenReturn(null);

        assertThatThrownBy(() -> hotelService.submitForApproval(hotelId, ownerId))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Vui lòng tải lên ảnh thumbnail");
    }

    @Test
    void deleteDraftHotel_Success() {
        UUID ownerId = UUID.randomUUID();
        UUID hotelId = UUID.randomUUID();

        Hotel hotel = new Hotel();
        hotel.setId(hotelId);
        hotel.setOwnerId(ownerId);
        hotel.setApprovedStatus(HotelApprovalStatus.DRAFT);

        when(hotelRepository.findById(hotelId)).thenReturn(Optional.of(hotel));
        when(mediaAssetRepository.findByTargetIdAndTargetType(hotelId, MediaTargetType.HOTEL)).thenReturn(Collections.emptyList());

        hotelService.deleteDraftHotel(hotelId, ownerId);

        verify(hotelRepository).delete(hotel);
    }

    @Test
    void deleteDraftHotel_WhenNotDraft_ShouldThrowIllegalState() {
        UUID ownerId = UUID.randomUUID();
        UUID hotelId = UUID.randomUUID();

        Hotel hotel = new Hotel();
        hotel.setId(hotelId);
        hotel.setOwnerId(ownerId);
        hotel.setApprovedStatus(HotelApprovalStatus.PENDING);

        when(hotelRepository.findById(hotelId)).thenReturn(Optional.of(hotel));

        assertThatThrownBy(() -> hotelService.deleteDraftHotel(hotelId, ownerId))
                .isInstanceOf(IllegalStateException.class);
    }
}

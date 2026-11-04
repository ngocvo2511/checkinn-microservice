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

    private AccommodationTypeService accommodationTypes;
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

        accommodationTypes = new AccommodationTypeService(hotelRepository, mock(com.example.hotelservice.Hotel.repository.AccommodationTypeChangeRepository.class));
        when(hotelRepository.findForUpdate(any())).thenAnswer(inv -> hotelRepository.findById(inv.getArgument(0)));
        hotelService = new HotelServiceImpl(
                accommodationTypes,
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

    @Test void newDraftRequiresAnExplicitTypeEvenOutsideController() throws Exception {
        var request = new com.fasterxml.jackson.databind.ObjectMapper().readValue("{}", HotelCreateRequest.class);
        org.junit.jupiter.api.Assertions.assertThrows(IllegalArgumentException.class, () -> hotelService.createHotel(request, UUID.randomUUID()));
        verify(hotelRepository, never()).save(any());
    }

    @Test void initialApprovalRejectsAnOlderRevision() {
        UUID id=UUID.randomUUID();
        Hotel hotel=Hotel.builder().id(id).revision(7).approvedStatus(com.example.hotelservice.Hotel.enums.HotelApprovalStatus.PENDING).isActive(false).build();
        when(hotelRepository.findById(id)).thenReturn(Optional.of(hotel));
        org.junit.jupiter.api.Assertions.assertThrows(org.springframework.web.server.ResponseStatusException.class,()->hotelService.approveHotel(id,6));
        org.junit.jupiter.api.Assertions.assertEquals(com.example.hotelservice.Hotel.enums.HotelApprovalStatus.PENDING,hotel.getApprovedStatus());
        hotelService.approveHotel(id,7);
        org.junit.jupiter.api.Assertions.assertTrue(hotel.getIsActive());
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
    void createHotel_WhenCoordinatesMissing_ShouldRemainUnknown() throws Exception {
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
        , com.example.hotelservice.Hotel.enums.AccommodationType.HOTEL);

        when(hotelRepository.save(any(Hotel.class))).thenAnswer(invocation -> {
            Hotel h = invocation.getArgument(0);
            h.setId(UUID.randomUUID());
            return h;
        });

        Hotel created = hotelService.createHotel(request, ownerId);

        assertThat(created).isNotNull();
        assertThat(created.getApprovedStatus()).isEqualTo(HotelApprovalStatus.DRAFT);
        verify(cityRepository, never()).findById(cityId);
        verify(objectMapper).writeValueAsString(org.mockito.ArgumentMatchers.argThat(obj -> {
            if (obj instanceof HotelAddressDto addr) {
                return addr.latitude() == null && addr.longitude() == null && "UNKNOWN".equals(addr.coordinateSource());
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
        , null);

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
    void createHotel_WhenCoordinatesZero_ShouldRemainUnknown() throws Exception {
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
        , com.example.hotelservice.Hotel.enums.AccommodationType.HOTEL);

        when(hotelRepository.save(any(Hotel.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Hotel created = hotelService.createHotel(request, ownerId);

        assertThat(created).isNotNull();
        assertThat(created.getApprovedStatus()).isEqualTo(HotelApprovalStatus.DRAFT);
        verify(cityRepository, never()).findById(cityId);
        verify(objectMapper).writeValueAsString(org.mockito.ArgumentMatchers.argThat(obj -> {
            if (obj instanceof HotelAddressDto addr) {
                return addr.latitude() == null && addr.longitude() == null && "UNKNOWN".equals(addr.coordinateSource());
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
        , com.example.hotelservice.Hotel.enums.AccommodationType.HOTEL);

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
    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.CsvSource({"DRAFT,true", "PENDING,true", "REJECTED,true", "APPROVED,false", "APPROVED,null", "null,true"})
    void publicDetailHidesUnpublishedHotels(String status, String active) {
        UUID id = UUID.randomUUID();
        Hotel hotel = Hotel.builder().id(id).ownerId(UUID.randomUUID())
                .approvedStatus("null".equals(status) ? null : HotelApprovalStatus.valueOf(status))
                .isActive("null".equals(active) ? null : Boolean.valueOf(active)).build();
        when(hotelRepository.findById(id)).thenReturn(Optional.of(hotel));
        assertThatThrownBy(() -> hotelService.getDetail(id)).isInstanceOf(jakarta.persistence.EntityNotFoundException.class);
        verify(hotelMapper, never()).toHotelResponse(any());
    }

    @Test
    void publicDetailMapsActiveApprovedHotel() {
        UUID id = UUID.randomUUID();
        Hotel hotel = Hotel.builder().id(id).isActive(true).approvedStatus(HotelApprovalStatus.APPROVED).build();
        when(hotelRepository.findById(id)).thenReturn(Optional.of(hotel));
        hotelService.getDetail(id);
        verify(hotelMapper).toPublicHotelResponse(hotel);
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.EnumSource(HotelApprovalStatus.class)
    void ownerAndAdminPreviewRetainUnpublishedState(HotelApprovalStatus status) {
        UUID id = UUID.randomUUID(), owner = UUID.randomUUID();
        Hotel hotel = Hotel.builder().id(id).ownerId(owner).isActive(false).approvedStatus(status).build();
        when(hotelRepository.findById(id)).thenReturn(Optional.of(hotel));
        hotelService.getPreview(id, owner, false);
        hotelService.getPreview(id, UUID.randomUUID(), true);
        verify(hotelMapper, org.mockito.Mockito.times(2)).toHotelResponse(hotel);
        assertThat(hotel.getApprovedStatus()).isEqualTo(status);
        assertThat(hotel.getIsActive()).isFalse();
    }

    @Test
    void previewRejectsUnrelatedActorAndMissingPrincipalBeforeMapping() {
        UUID id = UUID.randomUUID();
        when(hotelRepository.findById(id)).thenReturn(Optional.of(Hotel.builder().ownerId(UUID.randomUUID()).build()));
        assertThatThrownBy(() -> hotelService.getPreview(id, UUID.randomUUID(), false)).isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> hotelService.getPreview(id, null, true)).isInstanceOf(AccessDeniedException.class);
        verify(hotelMapper, never()).toHotelResponse(any());
    }

    @Test
    void missingPreviewReturnsNotFound() {
        assertThatThrownBy(() -> hotelService.getPreview(UUID.randomUUID(), UUID.randomUUID(), false))
                .isInstanceOf(jakarta.persistence.EntityNotFoundException.class);
    }

    @Test
    void publicListsUsePublicationQueriesAndOwnListsKeepUnpublishedHotels() {
        UUID city = UUID.randomUUID(), owner = UUID.randomUUID();
        hotelService.getAllApprovedHotels();
        hotelService.getByCity(city);
        hotelService.searchByName("fixture");
        verify(hotelRepository).findByIsActiveTrueAndApprovedStatus(HotelApprovalStatus.APPROVED);
        verify(hotelRepository).findByCityIdAndIsActiveTrueAndApprovedStatus(city, HotelApprovalStatus.APPROVED);
        verify(hotelRepository).findByNameContainingIgnoreCaseAndIsActiveTrueAndApprovedStatus("fixture", HotelApprovalStatus.APPROVED);
        Hotel draft = Hotel.builder().ownerId(owner).approvedStatus(HotelApprovalStatus.DRAFT).isActive(false).build();
        when(hotelRepository.findByOwnerId(owner)).thenReturn(List.of(draft));
        when(hotelRepository.findByOwnerIdAndCityId(owner, city)).thenReturn(List.of(draft));
        assertThat(hotelService.getByOwner(owner)).containsExactly(draft);
        assertThat(hotelService.getByOwnerAndCity(owner, city)).containsExactly(draft);
    }

    @Test void cityChangeWithoutAddressFailsBeforeMutatingHotel() {
        UUID id = UUID.randomUUID(), owner = UUID.randomUUID(), oldCity = UUID.randomUUID();
        Hotel hotel = Hotel.builder().id(id).ownerId(owner).cityId(oldCity).address("old-address").build();
        when(hotelRepository.findById(id)).thenReturn(Optional.of(hotel));
        HotelUpdateRequest request = new HotelUpdateRequest(null,null,null,UUID.randomUUID(),null,null,null,null,null,null,null,null,null,null,null, null);
        assertThatThrownBy(() -> hotelService.updateHotel(id, request, owner)).isInstanceOf(IllegalArgumentException.class);
        assertThat(hotel.getCityId()).isEqualTo(oldCity); assertThat(hotel.getAddress()).isEqualTo("old-address");
        verify(hotelRepository, never()).save(any());
    }
    @Test void cityChangeResolvesNewCityAndDropsOldCoordinates() throws Exception {
        UUID id = UUID.randomUUID(), owner = UUID.randomUUID(), newCity = UUID.randomUUID();
        Hotel hotel = Hotel.builder().id(id).ownerId(owner).cityId(UUID.randomUUID()).address("old-address").build();
        when(hotelRepository.findById(id)).thenReturn(Optional.of(hotel));
        when(cityRepository.findById(newCity)).thenReturn(Optional.of(City.builder().id(newCity).name("New city").latitude(12.0).longitude(108.0).build()));
        HotelUpdateRequest request = new HotelUpdateRequest(null,null,null,newCity,new HotelAddressDto("New street","Stale city","Vietnam",null,null),null,null,null,null,null,null,null,null,null,null, null);
        hotelService.updateHotel(id, request, owner);
        verify(objectMapper).writeValueAsString(org.mockito.ArgumentMatchers.argThat(value -> value instanceof HotelAddressDto address
                && "New city".equals(address.city()) && address.latitude() == null && "UNKNOWN".equals(address.coordinateSource())));
    }
    @Test void legacyAndCityCoordinatesAreNeverAdvertisedAsHotelPin() throws Exception {
        var mapper = new ObjectMapper();
        var legacy = mapper.readValue("{\"street\":\"Street\",\"city\":\"City\",\"country\":\"VN\",\"latitude\":12,\"longitude\":108}", HotelAddressDto.class).forRead();
        assertThat(legacy.coordinateSource()).isEqualTo("LEGACY_UNVERIFIED"); assertThat(legacy.latitude()).isNull();
        assertThat(new HotelAddressDto("Street","City","VN",12.0,108.0,"CITY_CENTROID").forRead().latitude()).isNull();
        assertThat(new HotelAddressDto("Street","City","VN",12.0,108.0,"OWNER_PROVIDED").forRead().latitude()).isEqualTo(12.0);
    }

}

package com.example.hotelservice.Hotel.mapper;

import com.checkinn.user.grpc.UserResponse;
import com.example.hotelservice.Amenity.dto.response.AmenityItemResponse;
import com.example.hotelservice.Amenity.dto.response.AmenityResponse;
import com.example.hotelservice.Amenity.entity.HotelAmenity;
import com.example.hotelservice.Amenity.entity.HotelAmenityCategory;
import com.example.hotelservice.Hotel.dto.response.OwnerResponse;
import com.example.hotelservice.Hotel.dto.response.PendingHotelDetailResponse;
import com.example.hotelservice.Hotel.dto.response.PendingHotelResponse;
import com.example.hotelservice.Hotel.entity.Hotel;
import com.example.hotelservice.Hotel.dto.request.HotelCreateRequest;
import com.example.hotelservice.Hotel.dto.request.HotelAddressDto;
import com.example.hotelservice.Hotel.dto.response.HotelResponse;
import com.example.hotelservice.MediaAsset.dto.response.MediaAssetResponse;
import com.example.hotelservice.MediaAsset.entity.MediaAsset;
import com.example.hotelservice.Policy.dto.response.PolicyResponse;
import com.example.hotelservice.Question.dto.QuestionResponse;
import com.example.hotelservice.Question.repository.QuestionRepository;
import com.example.hotelservice.Room.dto.response.RoomTypeResponse;
import com.example.hotelservice.Room.entity.RoomType;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.mapstruct.*;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@Mapper(componentModel = "spring")
public abstract class HotelMapper {

    protected ObjectMapper objectMapper;
    @Autowired
    public void setObjectMapper(ObjectMapper om) {
        this.objectMapper = om;
    }
    
    @Autowired
    protected com.example.hotelservice.MediaAsset.mapper.MediaAssetMapper mediaAssetMapper;

    @Autowired
    protected com.example.hotelservice.Room.mapper.RoomTypeMapper roomTypeMapper;

    @org.springframework.beans.factory.annotation.Autowired
    protected com.example.hotelservice.Room.repository.RoomTypeRepository inventoryRooms;

    @Autowired
    protected com.example.hotelservice.Policy.repository.HotelPolicyRepository hotelPolicyRepository;

    @Autowired
    protected com.example.hotelservice.MediaAsset.repository.MediaAssetRepository mediaAssetRepository;

        @Autowired
        protected QuestionRepository questionRepository;

    @Autowired
    protected com.example.hotelservice.Amenity.repository.HotelAmenityCategoryRepository hotelAmenityCategoryRepository;

    @Autowired
    protected com.example.hotelservice.Amenity.repository.HotelAmenityRepository hotelAmenityRepository;

    @Autowired
    protected com.example.hotelservice.Amenity.service.AmenityCatalogService amenityCatalog;

    // ----------- CreateHotelRequest -> Hotel -----------

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    @Mapping(target = "roomTypes", ignore = true)
    @Mapping(target = "mediaAssets", ignore = true)
    @Mapping(target = "address", expression = "java(writeJson(request.address()))")
    public abstract Hotel toHotel(HotelCreateRequest request);


    // ----------- Hotel -> HotelResponse (record) -----------

    @Mapping(target = "address", expression = "java(readAddress(entity.getAddress()))")
    @Mapping(target = "roomTypes", expression = "java(toRoomTypeResponses(entity.getRoomTypes()))")
    @Mapping(target = "mediaAssets", expression = "java(toMediaAssetResponses(entity.getMediaAssets()))")
    @Mapping(target = "lowestPrice", expression = "java(calculateLowestPrice(entity.getRoomTypes()))")
    @Mapping(target = "policies", expression = "java(getPolicies(entity.getId()))")
    @Mapping(target = "amenityCategories", expression = "java(getAmenities(entity.getId()))")
        @Mapping(target = "faqs", expression = "java(getQuestions(entity.getId()))")
    @Mapping(target = "accommodationTypeLabel", expression = "java(entity.getAccommodationType() == null ? null : entity.getAccommodationType().getLabel())")
    public abstract HotelResponse toHotelResponse(Hotel entity);

    public HotelResponse toPublicHotelResponse(Hotel entity) {
        if (entity == null) return null;
        return toPublicHotelResponses(List.of(entity)).get(0);
    }

    /**
     * Public representations of the hotels of one response. Associated facts are read once for
     * all hotels (bounded ID chunks) instead of per hotel/room; the effective inventory approval
     * predicate is unchanged and evaluated within the caller's request, never cached.
     */
    public List<HotelResponse> toPublicHotelResponses(List<Hotel> hotels) {
        if (hotels == null || hotels.isEmpty()) return List.of();
        var facts = loadPublicFacts(hotels);
        var result = new java.util.ArrayList<HotelResponse>(hotels.size());
        for (var hotel : hotels) result.add(hotel == null ? null : toPublicHotelResponseWithFacts(hotel, facts));
        return java.util.Collections.unmodifiableList(result);
    }

    @InheritConfiguration(name = "toHotelResponse")
    // Internal owner identity is not part of the public catalog (owner views use toHotelResponse; Revenue uses /internal/reports).
    @Mapping(target = "ownerId", expression = "java(null)")
    @Mapping(target = "roomTypes", expression = "java(toPublishedRoomTypeResponses(entity.getRoomTypes(), facts))")
    @Mapping(target = "lowestPrice", expression = "java(calculateApprovedLowestPrice(entity.getRoomTypes(), facts.approvedRoomIds()))")
    @Mapping(target = "mediaAssets", expression = "java(toMediaAssetResponses(facts.hotelMedia(entity.getId())))")
    @Mapping(target = "policies", expression = "java(facts.policies(entity.getId()))")
    @Mapping(target = "faqs", expression = "java(facts.faqs(entity.getId()))")
    @Mapping(target = "amenityCategories", expression = "java(facts.amenities(entity.getId()))")
    protected abstract HotelResponse toPublicHotelResponseWithFacts(Hotel entity, @Context PublicHotelFacts facts);

    private static final int PUBLIC_READ_CHUNK = 500;

    protected PublicHotelFacts loadPublicFacts(List<Hotel> hotels) {
        var hotelIds = new java.util.ArrayList<UUID>(new java.util.LinkedHashSet<>(hotels.stream()
                .filter(java.util.Objects::nonNull).map(Hotel::getId).filter(java.util.Objects::nonNull).toList()));
        // Hotel.roomTypes is a @BatchSize lazy collection: the first access initializes the
        // collections of all candidate hotels in one statement. Media collections carry @Where
        // and are not batch-loadable (Batch8D native evidence), so media is read explicitly below.
        var activeRoomIds = new java.util.LinkedHashSet<UUID>();
        for (var hotel : hotels) {
            if (hotel == null || hotel.getRoomTypes() == null) continue;
            for (var room : hotel.getRoomTypes())
                if (Boolean.TRUE.equals(room.getIsActive()) && room.getId() != null) activeRoomIds.add(room.getId());
        }
        var roomIds = new java.util.ArrayList<>(activeRoomIds);
        var approved = new java.util.HashSet<UUID>();
        for (int offset = 0; offset < roomIds.size(); offset += PUBLIC_READ_CHUNK)
            approved.addAll(inventoryRooms.findEffectivelyApprovedRoomIds(roomIds.subList(offset, Math.min(offset + PUBLIC_READ_CHUNK, roomIds.size()))));

        var approvedRooms = roomIds.stream().filter(approved::contains).toList();
        Map<UUID, List<com.example.hotelservice.MediaAsset.entity.MediaAsset>> roomMedia = new java.util.HashMap<>();
        for (int offset = 0; offset < approvedRooms.size(); offset += PUBLIC_READ_CHUNK)
            for (var asset : mediaAssetRepository.findByTargetTypeAndTargetIdIn(com.example.hotelservice.MediaAsset.enums.MediaTargetType.ROOM_TYPE,
                    approvedRooms.subList(offset, Math.min(offset + PUBLIC_READ_CHUNK, approvedRooms.size()))))
                roomMedia.computeIfAbsent(asset.getTargetId(), ignored -> new java.util.ArrayList<>()).add(asset);
        Map<UUID, List<com.example.hotelservice.MediaAsset.entity.MediaAsset>> hotelMedia = new java.util.HashMap<>();
        Map<UUID, List<PolicyResponse>> policies = new java.util.HashMap<>();
        Map<UUID, List<QuestionResponse>> faqs = new java.util.HashMap<>();
        Map<UUID, List<AmenityResponse>> amenities = new java.util.HashMap<>();
        for (int offset = 0; offset < hotelIds.size(); offset += PUBLIC_READ_CHUNK) {
            var chunk = hotelIds.subList(offset, Math.min(offset + PUBLIC_READ_CHUNK, hotelIds.size()));
            Map<UUID, List<PolicyResponse>> chunkPolicies = new java.util.LinkedHashMap<>();
            for (var policy : hotelPolicyRepository.findAllByHotelIdIn(chunk))
                chunkPolicies.computeIfAbsent(policy.getHotel().getId(), ignored -> new java.util.ArrayList<>()).add(toPolicyResponse(policy));
            Map<UUID, List<QuestionResponse>> chunkFaqs = new java.util.LinkedHashMap<>();
            for (var question : questionRepository.findAllByHotelIdIn(chunk))
                chunkFaqs.computeIfAbsent(question.getHotel().getId(), ignored -> new java.util.ArrayList<>()).add(toQuestionResponse(question));
            var categoriesByHotel = hotelAmenityCategoryRepository.findAllByHotelIdIn(chunk).stream()
                    .collect(java.util.stream.Collectors.groupingBy(HotelAmenityCategory::getHotelId, java.util.LinkedHashMap::new, java.util.stream.Collectors.toList()));
            var items = hotelAmenityRepository.findAllByCategoryHotelIdIn(chunk);
            var verifiedCodes = amenityCatalog.effectiveCodesForHotels(chunk);
            for (var asset : mediaAssetRepository.findByTargetTypeAndTargetIdIn(com.example.hotelservice.MediaAsset.enums.MediaTargetType.HOTEL, chunk))
                hotelMedia.computeIfAbsent(asset.getTargetId(), ignored -> new java.util.ArrayList<>()).add(asset);
            for (var id : chunk) {
                policies.put(id, List.copyOf(chunkPolicies.getOrDefault(id, List.of())));
                faqs.put(id, List.copyOf(chunkFaqs.getOrDefault(id, List.of())));
                // Items are grouped by their (hotel-unique) category inside assembleAmenities.
                amenities.put(id, assembleAmenities(categoriesByHotel.getOrDefault(id, List.of()), items, verifiedCodes, true));
            }
        }
        return new PublicHotelFacts(java.util.Set.copyOf(approved), hotelMedia, roomMedia, policies, faqs, amenities);
    }

    /** Internal global-filter/eligibility facts. Full DTO mapping happens after the page boundary. */
    @Named("searchCandidate")
    @BeanMapping(ignoreByDefault = true)
    @Mapping(target = "id", source = "id")
    @Mapping(target = "starRating", source = "starRating")
    @Mapping(target = "roomTypes", expression = "java(toSearchRoomTypeResponses(entity.getRoomTypes()))")
    @Mapping(target = "amenityCategories", expression = "java(getPublicAmenities(entity.getId()))")
    // Explicit constructor-component ignores are required by this MapStruct version for records.
    @Mapping(target = "ownerId", ignore = true)
    @Mapping(target = "cityId", ignore = true)
    @Mapping(target = "name", ignore = true)
    @Mapping(target = "description", ignore = true)
    @Mapping(target = "address", ignore = true)
    @Mapping(target = "contactEmail", ignore = true)
    @Mapping(target = "contactPhone", ignore = true)
    @Mapping(target = "policies", ignore = true)
    @Mapping(target = "faqs", ignore = true)
    @Mapping(target = "isActive", ignore = true)
    @Mapping(target = "approvedStatus", ignore = true)
    @Mapping(target = "city", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    @Mapping(target = "lowestPrice", ignore = true)
    @Mapping(target = "mediaAssets", ignore = true)
    @Mapping(target = "accommodationType", ignore = true)
    @Mapping(target = "accommodationTypeLabel", ignore = true)
    public abstract HotelResponse toSearchCandidateResponse(Hotel entity);

    // Inherited record constructor ignores are not honored by the current generator.
    // Explicit internal construction keeps detail getters out of this batch-read path.
    @Named("searchCandidateWithFacts")
    public HotelResponse toSearchCandidateResponseWithFacts(Hotel entity, List<RoomType> rooms,
                                                           List<AmenityResponse> publicAmenities) {
        if (entity == null) return null;
        return new HotelResponse(entity.getId(),null,null,null,null,entity.getStarRating(),
                null,null,null,null,null,publicAmenities,null,null,null,
                null,null,null,toSearchRoomTypeResponses(rooms),null,null,null);
    }

    protected List<RoomTypeResponse> toSearchRoomTypeResponses(List<RoomType> rooms) {
        if (rooms == null) return List.of();
        return rooms.stream().filter(room -> Boolean.TRUE.equals(room.getIsActive()))
                .map(roomTypeMapper::toSearchCandidateResponse).toList();
    }

    protected List<RoomTypeResponse> toPublishedRoomTypeResponses(List<RoomType> rooms, PublicHotelFacts facts) {
        if (rooms == null) return List.of();
        return rooms.stream().filter(room -> Boolean.TRUE.equals(room.getIsActive()))
                .filter(room -> facts.approvedRoomIds().contains(room.getId()))
                .map(room -> roomTypeMapper.toRoomTypeResponseWithMedia(room, facts.roomMedia(room.getId()))).toList();
    }

    protected List<RoomTypeResponse> toPublishedRoomTypeResponses(List<RoomType> rooms, java.util.Set<UUID> approved) {
        if (rooms == null) return List.of();
        return rooms.stream().filter(room -> Boolean.TRUE.equals(room.getIsActive()))
                .filter(room -> approved.contains(room.getId()))
                .map(roomTypeMapper::toRoomTypeResponse).toList();
    }


    // ----------- Hotel -> PendingHotelResponse (record) -----------
    public PendingHotelResponse toPendingHotelResponse(Hotel hotel) {

        if (hotel == null) {
            return null;
        }

        return PendingHotelResponse.builder()
                .id(hotel.getId())
                .revision(hotel.getRevision())
                .accommodationType(hotel.getAccommodationType())
                .accommodationTypeLabel(hotel.getAccommodationType()==null?null:hotel.getAccommodationType().getLabel())
                .name(hotel.getName())
                .address(readAddress(hotel.getAddress())
                )
                .starRating(hotel.getStarRating())
                .approvedStatus(hotel.getApprovedStatus())
                .createdAt(hotel.getCreatedAt())
                .build();
    }

    public PendingHotelDetailResponse toPendingHotelDetailResponse(Hotel hotel,
                                                                   List<PolicyResponse> policies,
                                                                   List<AmenityResponse> amenityCategories,
                                                                   UserResponse ownerInfo,
                                                                   List<QuestionResponse> faqs) {

        if (hotel == null) {
            return null;
        }

        return PendingHotelDetailResponse.builder()
                .id(hotel.getId())
                .revision(hotel.getRevision())
                .accommodationType(hotel.getAccommodationType())
                .accommodationTypeLabel(hotel.getAccommodationType()==null?null:hotel.getAccommodationType().getLabel())
                .name(hotel.getName())
                .description(hotel.getDescription())
                .starRating(hotel.getStarRating())

                .address(readAddress(hotel.getAddress()))

                .contactEmail(hotel.getContactEmail())
                .contactPhone(hotel.getContactPhone())

                .createdAt(hotel.getCreatedAt())
                .approvedStatus(hotel.getApprovedStatus())

                .owner(mapOwner(ownerInfo))

                .businessLicenseNumber(hotel.getBusinessLicenseNumber())
                .taxId(hotel.getTaxId())
                .operationLicenseNumber(hotel.getOperationLicenseNumber())
                .ownerIdentityNumber(hotel.getOwnerIdentityNumber())

                .policies(policies)
                .amenityCategories(amenityCategories)
                .faqs(faqs)

                .mediaAssets(toMediaAssetResponses(hotel.getMediaAssets()))
                .build();
    }

    private OwnerResponse mapOwner(UserResponse user) {
        return OwnerResponse.builder()
                .id(user.getId())
                .fullName(user.getFullName())
                .email(user.getEmail())
                .role(user.getRole())
                .username(user.getUsername())
                .build();
    }


    // ---------- JSON Helpers ----------
    @Named("jsonColumn")
    protected String writeJson(Object value) {
        try { return objectMapper.writeValueAsString(value); }
        catch (Exception ex) { throw new RuntimeException(ex); }
    }

    protected HotelAddressDto readAddress(String json) {
        try { return objectMapper.readValue(json, HotelAddressDto.class).forRead(); }
        catch (Exception ex) { throw new RuntimeException(ex); }
    }

    protected List<MediaAssetResponse> toMediaAssetResponses(
            List<MediaAsset> assets
    ) {
        if (assets == null || assets.isEmpty()) return List.of();
        return assets.stream()
                .map(mediaAssetMapper::toMediaAssetResponse)
                .toList();
    }

    protected List<RoomTypeResponse> toRoomTypeResponses(
            List<RoomType> roomTypes
    ) {
        if (roomTypes == null) return java.util.List.of();
        return roomTypes.stream()
                .map(roomTypeMapper::toRoomTypeResponse)
                .toList();
    }

    protected java.math.BigDecimal calculateApprovedLowestPrice(List<RoomType> rooms, java.util.Set<UUID> approved) {
        if (rooms==null) return null;
        return calculateLowestPrice(rooms.stream()
                .filter(room -> approved.contains(room.getId())).toList());
    }

    protected java.math.BigDecimal calculateLowestPrice(
            List<RoomType> roomTypes
    ) {
        if (roomTypes == null || roomTypes.isEmpty()) return null;
        return roomTypes.stream()
                .filter(rt -> rt.getIsActive() != null && rt.getIsActive())
                .map(com.example.hotelservice.Room.entity.RoomType::getBasePrice)
                .filter(java.util.Objects::nonNull)
                .min(java.math.BigDecimal::compareTo)
                .orElse(null);
    }

    protected List<PolicyResponse> getPolicies(UUID hotelId) {
        if (hotelId == null) return List.of();
        return hotelPolicyRepository.findAllByHotelId(hotelId)
                .stream()
                .map(this::toPolicyResponse)
                .toList();
    }

    private PolicyResponse toPolicyResponse(com.example.hotelservice.Policy.entity.HotelPolicy p) {
        return PolicyResponse.builder()
                .category(p.getCategory().getName())
                .content(p.getContent())
                .build();
    }

    protected List<QuestionResponse> getQuestions(UUID hotelId) {
        if (hotelId == null) return List.of();
        return questionRepository.findAllByHotelId(hotelId)
                .stream()
                .map(this::toQuestionResponse)
                .toList();
    }

    private QuestionResponse toQuestionResponse(com.example.hotelservice.Question.entity.Question q) {
        return QuestionResponse.builder()
                .question(q.getQuestion())
                .answer(q.getAnswer())
                .build();
    }

    protected List<AmenityResponse> getAmenities(UUID hotelId) {
        return getAmenities(hotelId, false);
    }

    protected List<AmenityResponse> getPublicAmenities(UUID hotelId) {
        return getAmenities(hotelId, true);
    }

    private List<AmenityResponse> getAmenities(UUID hotelId, boolean publicOnly) {
        if (hotelId == null) return List.of();

        List<HotelAmenityCategory> categories =
                hotelAmenityCategoryRepository.findAllByHotelId(hotelId);

        List<HotelAmenity> amenities =
                hotelAmenityRepository.findAllByCategoryHotelId(hotelId);

        var verifiedCodes = amenityCatalog.effectiveCodes(hotelId);
        return assembleAmenities(categories, amenities, verifiedCodes, publicOnly);
    }

    /** Shared by per-hotel management mapping and the batched public facts: identical output rules. */
    private List<AmenityResponse> assembleAmenities(List<HotelAmenityCategory> categories, List<HotelAmenity> amenities,
            Map<UUID, List<com.example.hotelservice.Amenity.service.AmenityCatalogService.Code>> verifiedCodes, boolean publicOnly) {
        Map<UUID, List<HotelAmenity>> amenityMap =
                amenities.stream()
                        .filter(item -> !publicOnly || Boolean.TRUE.equals(item.getIsActive()))
                        .collect(java.util.stream.Collectors.groupingBy(
                                a -> a.getCategory().getId()
                        ));

        return categories.stream()
                .filter(category -> !publicOnly || Boolean.TRUE.equals(category.getIsActive()))
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
                                                .verifiedCodes(verifiedCodes.getOrDefault(item.getId(), List.of()))
                                                .build())
                                        .toList()
                        )
                        .build())
                .toList();
    }
}

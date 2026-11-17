package com.example.hotelservice.Hotel.mapper;

import com.example.hotelservice.Amenity.entity.HotelAmenity;
import com.example.hotelservice.Amenity.entity.HotelAmenityCategory;
import com.example.hotelservice.Amenity.repository.HotelAmenityCategoryRepository;
import com.example.hotelservice.Amenity.repository.HotelAmenityRepository;
import com.example.hotelservice.Amenity.service.AmenityCatalogService;
import com.example.hotelservice.Hotel.entity.Hotel;
import com.example.hotelservice.MediaAsset.entity.MediaAsset;
import com.example.hotelservice.MediaAsset.enums.MediaTargetType;
import com.example.hotelservice.Policy.entity.HotelPolicy;
import com.example.hotelservice.Policy.entity.PolicyCategoryType;
import com.example.hotelservice.Policy.repository.HotelPolicyRepository;
import com.example.hotelservice.Question.entity.Question;
import com.example.hotelservice.Question.repository.QuestionRepository;
import com.example.hotelservice.Room.entity.RoomType;
import com.example.hotelservice.Room.mapper.RoomTypeMapper;
import com.example.hotelservice.Room.repository.RoomTypeRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.mapstruct.factory.Mappers;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/** Batch8D: public facts are read once per response and grouped to the right hotel. */
class HotelMapperPublicBatchTest {
    private final ObjectMapper json = new ObjectMapper().findAndRegisterModules();
    private final RoomTypeRepository rooms = mock(RoomTypeRepository.class);
    private final HotelPolicyRepository policies = mock(HotelPolicyRepository.class);
    private final QuestionRepository questions = mock(QuestionRepository.class);
    private final HotelAmenityCategoryRepository categories = mock(HotelAmenityCategoryRepository.class);
    private final HotelAmenityRepository items = mock(HotelAmenityRepository.class);
    private final AmenityCatalogService catalog = mock(AmenityCatalogService.class);
    private final com.example.hotelservice.MediaAsset.repository.MediaAssetRepository media = mock(com.example.hotelservice.MediaAsset.repository.MediaAssetRepository.class);

    private HotelMapper mapper() {
        var mapper = Mappers.getMapper(HotelMapper.class);
        mapper.setObjectMapper(json);
        var mediaMapper = Mappers.getMapper(com.example.hotelservice.MediaAsset.mapper.MediaAssetMapper.class);
        var roomMapper = Mappers.getMapper(RoomTypeMapper.class);
        ReflectionTestUtils.setField(roomMapper, "objectMapper", json);
        ReflectionTestUtils.setField(roomMapper, "mediaAssetMapper", mediaMapper);
        mapper.mediaAssetMapper = mediaMapper;
        mapper.roomTypeMapper = roomMapper;
        mapper.inventoryRooms = rooms;
        mapper.hotelPolicyRepository = policies;
        mapper.questionRepository = questions;
        mapper.hotelAmenityCategoryRepository = categories;
        mapper.hotelAmenityRepository = items;
        mapper.amenityCatalog = catalog;
        mapper.mediaAssetRepository = media;
        return mapper;
    }

    private static RoomType room(UUID hotel, int price, boolean active) {
        // No lazy media list: public media must come from the bulk repository read.
        return RoomType.builder().id(UUID.randomUUID()).hotelId(hotel).name("Synthetic room " + price)
                .basePrice(BigDecimal.valueOf(price)).capacity("{\"adults\":2,\"children\":0}").isActive(active).totalRooms(2).build();
    }

    private static MediaAsset media(UUID target, MediaTargetType type, int order) {
        return MediaAsset.builder().id(UUID.randomUUID()).targetId(target).targetType(type)
                .url("https://example.invalid/" + order).isThumbnail(order == 0).sortOrder(order).build();
    }

    private static Hotel hotel(UUID id, List<RoomType> rooms) {
        return Hotel.builder().id(id).name("Synthetic hotel").isActive(true)
                .address("{\"street\":\"Synthetic street\",\"city\":\"Synthetic city\",\"country\":\"VN\"}")
                .roomTypes(rooms).build();
    }

    @Test void oneBulkReadPerFactForAllHotelsWithPerHotelGrouping() {
        var h1 = UUID.randomUUID(); var h2 = UUID.randomUUID();
        var approvedA = room(h1, 300, true);
        var pendingB = room(h1, 100, true);
        var inactiveC = room(h1, 50, false);
        var approvedD = room(h2, 200, true);
        var hotel1 = hotel(h1, List.of(approvedA, pendingB, inactiveC));
        var hotel2 = hotel(h2, List.of(approvedD));
        when(media.findByTargetTypeAndTargetIdIn(eq(MediaTargetType.ROOM_TYPE), any())).thenReturn(List.of(
                media(approvedA.getId(), MediaTargetType.ROOM_TYPE, 0), media(approvedA.getId(), MediaTargetType.ROOM_TYPE, 1)));
        when(media.findByTargetTypeAndTargetIdIn(eq(MediaTargetType.HOTEL), any())).thenReturn(List.of(
                media(h1, MediaTargetType.HOTEL, 0), media(h1, MediaTargetType.HOTEL, 1)));
        when(rooms.findEffectivelyApprovedRoomIds(any())).thenReturn(List.of(approvedA.getId(), approvedD.getId()));
        var policyHotel = Hotel.builder().id(h1).build();
        when(policies.findAllByHotelIdIn(any())).thenReturn(List.of(HotelPolicy.builder().id(UUID.randomUUID()).hotel(policyHotel)
                .category(PolicyCategoryType.builder().id(UUID.randomUUID()).name("Check-in").build()).content("From 14:00").build()));
        var question = Question.builder().id(UUID.randomUUID()).hotel(Hotel.builder().id(h2).build()).question("Parking?").answer("Yes").build();
        when(questions.findAllByHotelIdIn(any())).thenReturn(List.of(question));
        var active = HotelAmenityCategory.builder().id(UUID.randomUUID()).hotelId(h1).title("General").isActive(true).build();
        var hidden = HotelAmenityCategory.builder().id(UUID.randomUUID()).hotelId(h1).title("Hidden").isActive(false).build();
        var activeItem = HotelAmenity.builder().id(UUID.randomUUID()).category(active).title("Wi-Fi").isActive(true).build();
        when(categories.findAllByHotelIdIn(any())).thenReturn(List.of(active, hidden));
        when(items.findAllByCategoryHotelIdIn(any())).thenReturn(List.of(activeItem,
                HotelAmenity.builder().id(UUID.randomUUID()).category(active).title("Inactive item").isActive(false).build(),
                HotelAmenity.builder().id(UUID.randomUUID()).category(hidden).title("Hidden category item").isActive(true).build()));
        when(catalog.effectiveCodesForHotels(any())).thenReturn(Map.of(activeItem.getId(), List.of(new AmenityCatalogService.Code("WIFI_FREE", "Wi-Fi miễn phí"))));

        var result = mapper().toPublicHotelResponses(List.of(hotel1, hotel2));

        verify(rooms, times(1)).findEffectivelyApprovedRoomIds(List.of(approvedA.getId(), pendingB.getId(), approvedD.getId()));
        // Media only for approved active rooms (never pending/inactive) and once for all hotels.
        verify(media, times(1)).findByTargetTypeAndTargetIdIn(MediaTargetType.ROOM_TYPE, List.of(approvedA.getId(), approvedD.getId()));
        verify(media, times(1)).findByTargetTypeAndTargetIdIn(MediaTargetType.HOTEL, List.of(h1, h2));
        verify(policies, times(1)).findAllByHotelIdIn(List.of(h1, h2));
        verify(questions, times(1)).findAllByHotelIdIn(List.of(h1, h2));
        verify(categories, times(1)).findAllByHotelIdIn(List.of(h1, h2));
        verify(items, times(1)).findAllByCategoryHotelIdIn(List.of(h1, h2));
        verify(catalog, times(1)).effectiveCodesForHotels(List.of(h1, h2));
        verify(policies, never()).findAllByHotelId(any());
        verify(questions, never()).findAllByHotelId(any());
        verify(categories, never()).findAllByHotelId(any());
        verify(items, never()).findAllByCategoryHotelId(any());
        verify(catalog, never()).effectiveCodes(any(UUID.class));

        var first = result.get(0); var second = result.get(1);
        assertThat(first.id()).isEqualTo(h1);
        assertThat(first.roomTypes()).extracting(r -> r.id()).containsExactly(approvedA.getId());
        assertThat(first.roomTypes().get(0).mediaAssets()).hasSize(2);
        assertThat(first.lowestPrice()).isEqualByComparingTo("300");
        assertThat(first.mediaAssets()).hasSize(2);
        assertThat(first.policies()).extracting(p -> p.getCategory()).containsExactly("Check-in");
        assertThat(first.faqs()).isEmpty();
        assertThat(first.amenityCategories()).hasSize(1);
        assertThat(first.amenityCategories().get(0).getItems()).extracting(i -> i.getTitle()).containsExactly("Wi-Fi");
        assertThat(first.amenityCategories().get(0).getItems().get(0).getVerifiedCodes()).extracting(c -> c.code()).containsExactly("WIFI_FREE");
        assertThat(second.id()).isEqualTo(h2);
        assertThat(second.roomTypes()).extracting(r -> r.id()).containsExactly(approvedD.getId());
        assertThat(second.roomTypes().get(0).mediaAssets()).isEmpty();
        assertThat(second.mediaAssets()).isEmpty();
        assertThat(second.policies()).isEmpty();
        assertThat(second.faqs()).extracting(q -> q.getQuestion()).containsExactly("Parking?");
        assertThat(second.amenityCategories()).isEmpty();
    }

    @Test void singleDetailUsesTheSameBatchedPathAndNoApprovalReadWithoutActiveRooms() {
        var id = UUID.randomUUID();
        var inactive = room(id, 100, false);
        var single = mapper().toPublicHotelResponse(hotel(id, List.of(inactive)));
        assertThat(single.roomTypes()).isEmpty();
        assertThat(single.lowestPrice()).isNull();
        assertThat(single.mediaAssets()).isEmpty();
        verify(rooms, never()).findEffectivelyApprovedRoomIds(any());
        verify(policies, times(1)).findAllByHotelIdIn(List.of(id));
        assertThat(mapper().toPublicHotelResponses(List.of())).isEmpty();
        assertThat(mapper().toPublicHotelResponse(null)).isNull();
        verifyNoMoreInteractions(rooms);
    }

    @Test void approvalStaysAuthoritativeEvenWhenTotalRoomsArePositive() {
        var id = UUID.randomUUID();
        var unapproved = room(id, 100, true);
        when(rooms.findEffectivelyApprovedRoomIds(any())).thenReturn(List.of());
        var response = mapper().toPublicHotelResponses(List.of(hotel(id, List.of(unapproved)))).get(0);
        verify(media, never()).findByTargetTypeAndTargetIdIn(eq(MediaTargetType.ROOM_TYPE), any());
        assertThat(response.roomTypes()).isEmpty();
        assertThat(response.lowestPrice()).isNull();
        assertThat(Set.copyOf(response.roomTypes())).isEmpty();
    }
}

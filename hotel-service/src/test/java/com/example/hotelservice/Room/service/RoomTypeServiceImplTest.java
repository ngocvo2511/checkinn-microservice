package com.example.hotelservice.Room.service;

import com.example.hotelservice.Hotel.entity.Hotel;
import com.example.hotelservice.Hotel.repository.HotelRepository;
import com.example.hotelservice.Room.dto.request.CapacityDto;
import com.example.hotelservice.Room.dto.request.RoomTypeCreateRequest;
import com.example.hotelservice.Room.dto.request.RoomTypeUpdateRequest;
import com.example.hotelservice.Room.entity.RoomType;
import com.example.hotelservice.Room.repository.RoomTypeRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RoomTypeServiceImplTest {

    private RoomTypeRepository roomTypeRepository;
    private HotelRepository hotelRepository;
    private RoomTypeServiceImpl roomTypeService;

    @BeforeEach
    void setUp() {
        roomTypeRepository = mock(RoomTypeRepository.class);
        hotelRepository = mock(HotelRepository.class);
        roomTypeService = new RoomTypeServiceImpl(roomTypeRepository, hotelRepository, new ObjectMapper(), org.mockito.Mockito.mock(RoomInventoryReviewService.class));
    }

    @Test void metadataUsesCurrentLockedSnapshotWithoutRewritingASeparatePriceOrStockChange() {
        UUID hotelId=UUID.randomUUID(),ownerId=UUID.randomUUID(),id=UUID.randomUUID();
        var stale=roomType(id,hotelId,2,true);var current=roomType(id,hotelId,4,true);current.setBasePrice(BigDecimal.valueOf(2_000_000));
        when(roomTypeRepository.findById(id)).thenReturn(Optional.of(stale));
        when(roomTypeRepository.findByIdForUpdate(id)).thenReturn(Optional.of(current));
        when(hotelRepository.findById(hotelId)).thenReturn(Optional.of(hotel(hotelId,ownerId)));
        when(roomTypeRepository.save(any())).thenAnswer(call->call.getArgument(0));
        var result=roomTypeService.updateRoomType(id,new RoomTypeUpdateRequest(null,null,null,null,null,null,"Updated metadata"),ownerId);
        assertThat(result.getBasePrice()).isEqualByComparingTo("2000000");assertThat(result.getTotalRooms()).isEqualTo(4);assertThat(result.getDescription()).isEqualTo("Updated metadata");assertThat(stale.getDescription()).isNull();
    }
    @Test void roomAmountGuardUsesCurrentValueAfterCompetingIncrease() {
        UUID hotelId=UUID.randomUUID(),ownerId=UUID.randomUUID(),id=UUID.randomUUID();
        when(roomTypeRepository.findById(id)).thenReturn(Optional.of(roomType(id,hotelId,2,true)));
        when(roomTypeRepository.findByIdForUpdate(id)).thenReturn(Optional.of(roomType(id,hotelId,4,true)));
        when(hotelRepository.findById(hotelId)).thenReturn(Optional.of(hotel(hotelId,ownerId)));
        assertThatThrownBy(()->roomTypeService.updateRoomType(id,new RoomTypeUpdateRequest(null,null,null,null,null,3),ownerId)).isInstanceOf(IllegalArgumentException.class);
        verify(roomTypeRepository,never()).save(any());
    }
    @Test
    void createRoomTypeRejectsUnauthorizedOwner() {
        UUID hotelId = UUID.randomUUID();
        UUID actualOwner = UUID.randomUUID();
        UUID otherOwner = UUID.randomUUID();
        when(hotelRepository.findForUpdate(hotelId)).thenReturn(Optional.of(hotel(hotelId, actualOwner)));

        assertThatThrownBy(() -> roomTypeService.createRoomType(createRequest(hotelId, 2), otherOwner))
                .isInstanceOf(SecurityException.class);

        verify(roomTypeRepository, never()).save(any());
    }

    @Test
    void createRoomTypeRejectsRoomAmountLowerThanOne() {
        UUID hotelId = UUID.randomUUID();
        UUID ownerId = UUID.randomUUID();
        when(hotelRepository.findForUpdate(hotelId)).thenReturn(Optional.of(hotel(hotelId, ownerId)));

        assertThatThrownBy(() -> roomTypeService.createRoomType(createRequest(hotelId, 0), ownerId))
                .isInstanceOf(IllegalArgumentException.class);

        verify(roomTypeRepository, never()).save(any());
    }

    @Test
    void updateRoomTypeRejectsRoomAmountDecrease() {
        UUID hotelId = UUID.randomUUID();
        UUID ownerId = UUID.randomUUID();
        UUID roomTypeId = UUID.randomUUID();
        RoomType roomType = roomType(roomTypeId, hotelId, 5, true);

        when(roomTypeRepository.findByIdForUpdate(roomTypeId)).thenReturn(Optional.of(roomType));
        when(hotelRepository.findById(hotelId)).thenReturn(Optional.of(hotel(hotelId, ownerId)));

        RoomTypeUpdateRequest request = new RoomTypeUpdateRequest(null, null, null, null, null, 4);

        assertThatThrownBy(() -> roomTypeService.updateRoomType(roomTypeId, request, ownerId))
                .isInstanceOf(IllegalArgumentException.class);

        verify(roomTypeRepository, never()).save(any());
    }

    @Test
    void deactivateAndActivateRoomTypeOnlyWhenOwnerMatches() {
        UUID hotelId = UUID.randomUUID();
        UUID ownerId = UUID.randomUUID();
        UUID roomTypeId = UUID.randomUUID();
        RoomType roomType = roomType(roomTypeId, hotelId, 5, true);

        when(roomTypeRepository.findByIdForUpdate(roomTypeId)).thenReturn(Optional.of(roomType));
        when(hotelRepository.findById(hotelId)).thenReturn(Optional.of(hotel(hotelId, ownerId)));

        roomTypeService.deactivateRoomType(roomTypeId, ownerId);
        assertThat(roomType.getIsActive()).isFalse();

        roomTypeService.activateRoomType(roomTypeId, ownerId);
        assertThat(roomType.getIsActive()).isTrue();
    }

    @Test void unknownLegacyInventoryRequiresReviewBeforeAssigningAnAmount() {
        UUID hotelId=UUID.randomUUID(), ownerId=UUID.randomUUID(), roomId=UUID.randomUUID();
        RoomType room=roomType(roomId,hotelId,5,true); room.setTotalRooms(null);
        when(roomTypeRepository.findByIdForUpdate(roomId)).thenReturn(Optional.of(room));
        when(hotelRepository.findById(hotelId)).thenReturn(Optional.of(hotel(hotelId,ownerId)));
        assertThatThrownBy(() -> roomTypeService.updateRoomType(roomId,new RoomTypeUpdateRequest(null,null,null,null,null,5),ownerId))
            .isInstanceOf(com.example.hotelservice.Room.availability.service.InventoryNotConfiguredException.class);
        verify(roomTypeRepository,never()).save(any());
    }

    private RoomTypeCreateRequest createRequest(UUID hotelId, int roomAmount) {
        return new RoomTypeCreateRequest(
                hotelId,
                "RTM Room",
                BigDecimal.valueOf(1_000_000),
                new CapacityDto(2, 1, "Queen", 28, true, 2),
                roomAmount,
                List.of("Wifi")
        );
    }

    private Hotel hotel(UUID hotelId, UUID ownerId) {
        return Hotel.builder()
                .id(hotelId)
                .ownerId(ownerId)
                .name("RTM Hotel")
                .build();
    }

    private RoomType roomType(UUID roomTypeId, UUID hotelId, int totalRooms, boolean active) {
        return RoomType.builder()
                .id(roomTypeId)
                .hotelId(hotelId)
                .name("RTM Room")
                .basePrice(BigDecimal.valueOf(1_000_000))
                .totalRooms(totalRooms)
                .isActive(active)
                .build();
    }
    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.EnumSource(com.example.hotelservice.Hotel.enums.HotelApprovalStatus.class)
    void publicRoomReadsRequirePublishedParentButPreviewKeepsAllStates(com.example.hotelservice.Hotel.enums.HotelApprovalStatus status) {
        UUID id = UUID.randomUUID(), hotelId = UUID.randomUUID(), owner = UUID.randomUUID();
        Hotel hotel = hotel(hotelId, owner); hotel.setApprovedStatus(status); hotel.setIsActive(true);
        RoomType room = new RoomType(); room.setId(id); room.setHotelId(hotelId); room.setIsActive(true);
        when(roomTypeRepository.findById(id)).thenReturn(Optional.of(room));
        when(roomTypeRepository.findByHotelId(hotelId)).thenReturn(List.of(room));
        when(hotelRepository.findById(hotelId)).thenReturn(Optional.of(hotel));
        if (status == com.example.hotelservice.Hotel.enums.HotelApprovalStatus.APPROVED) {
            assertThat(roomTypeService.getPublicById(id)).isSameAs(room);
            assertThat(roomTypeService.getPublicByHotel(hotelId)).containsExactly(room);
        } else {
            assertThatThrownBy(() -> roomTypeService.getPublicById(id)).isInstanceOf(jakarta.persistence.EntityNotFoundException.class);
            assertThatThrownBy(() -> roomTypeService.getPublicByHotel(hotelId)).isInstanceOf(jakarta.persistence.EntityNotFoundException.class);
        }
        assertThat(roomTypeService.getPreviewById(id, owner, false)).isSameAs(room);
        assertThat(roomTypeService.getPreviewByHotel(hotelId, UUID.randomUUID(), true)).containsExactly(room);
        assertThatThrownBy(() -> roomTypeService.getPreviewById(id, UUID.randomUUID(), false)).isInstanceOf(org.springframework.security.access.AccessDeniedException.class);
    }

    @Test void inactiveRoomHiddenAndStillEditableInOwnPreview() {
        UUID id = UUID.randomUUID(), hotelId = UUID.randomUUID(), owner = UUID.randomUUID();
        Hotel hotel = hotel(hotelId, owner); hotel.setIsActive(true); hotel.setApprovedStatus(com.example.hotelservice.Hotel.enums.HotelApprovalStatus.APPROVED);
        RoomType room = new RoomType(); room.setId(id); room.setHotelId(hotelId); room.setIsActive(false);
        when(roomTypeRepository.findById(id)).thenReturn(Optional.of(room));
        when(roomTypeRepository.findByHotelId(hotelId)).thenReturn(List.of(room));
        when(hotelRepository.findById(hotelId)).thenReturn(Optional.of(hotel));
        assertThatThrownBy(() -> roomTypeService.getPublicById(id)).isInstanceOf(jakarta.persistence.EntityNotFoundException.class);
        assertThat(roomTypeService.getPublicByHotel(hotelId)).isEmpty();
        assertThat(roomTypeService.getPreviewById(id, owner, false)).isSameAs(room);
        hotel.setIsActive(false);
        assertThatThrownBy(() -> roomTypeService.getPublicByHotel(hotelId)).isInstanceOf(jakarta.persistence.EntityNotFoundException.class);
    }

}

package com.example.hotelservice.Room.service;

import com.example.hotelservice.Hotel.entity.Hotel;
import com.example.hotelservice.Hotel.repository.HotelRepository;
import com.example.hotelservice.Room.entity.RoomType;
import com.example.hotelservice.Room.repository.RoomTypeRepository;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.server.ResponseStatusException;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class RoomInventoryReviewServiceTest {
    final JdbcTemplate jdbc=mock(JdbcTemplate.class);
    final RoomTypeRepository rooms=mock(RoomTypeRepository.class);
    final HotelRepository hotels=mock(HotelRepository.class);
    final RoomInventoryReviewService service=new RoomInventoryReviewService(jdbc,rooms,hotels);
    final UUID roomId=UUID.randomUUID(),hotelId=UUID.randomUUID(),owner=UUID.randomUUID(),requestId=UUID.randomUUID();
    final RoomInventoryReviewService.Actor actor=new RoomInventoryReviewService.Actor(owner,"OWNER");
    RoomType room() {
        RoomType room=new RoomType();room.setId(roomId);room.setHotelId(hotelId);room.setTotalRooms(10);
        Hotel hotel=new Hotel();hotel.setOwnerId(owner);
        when(rooms.findById(roomId)).thenReturn(Optional.of(room));when(rooms.findByIdForUpdate(roomId)).thenReturn(Optional.of(room));
        when(hotels.findById(hotelId)).thenReturn(Optional.of(hotel));return room;
    }
    @Test void unreviewedTenIsNeitherInvalidNorApprovedAndReadDoesNotCreateAudit() {
        room();var state=service.state(roomId,actor);
        assertThat(state.currentQuantity()).isEqualTo(10);assertThat(state.verified()).isFalse();assertThat(state.managed()).isFalse();
        verify(jdbc,never()).update(anyString(),any(Object[].class));verify(rooms,never()).save(any());
    }
    @Test void foreignOwnerIsDeniedBeforePrivateEvidenceRead() {
        room();assertThatThrownBy(()->service.state(roomId,new RoomInventoryReviewService.Actor(UUID.randomUUID(),"OWNER"))).isInstanceOf(SecurityException.class);
        verifyNoInteractions(jdbc);
    }
    @Test void onlyOwnerProposesAndOnlyAdminReviews() {
        assertThatThrownBy(()->service.propose(roomId,new RoomInventoryReviewService.Proposal(3,0L,"Source"),new RoomInventoryReviewService.Actor(owner,"ADMIN"))).isInstanceOf(SecurityException.class);
        assertThatThrownBy(()->service.review(requestId,new RoomInventoryReviewService.Review(true,1L,"Evidence"),actor)).isInstanceOf(SecurityException.class);
        verifyNoInteractions(jdbc,rooms,hotels);
    }
    @Test void managedStockCannotBeChangedThroughLegacyUpdateButUnchangedValueIsAllowed() {
        RoomType room=room();when(jdbc.queryForList("SELECT * FROM room_inventory_review_states WHERE room_id=?",roomId)).thenReturn(List.of(Map.of("revision",1L)));
        service.requireLegacyUpdateAllowed(room,10);
        assertThatThrownBy(()->service.requireLegacyUpdateAllowed(room,11)).isInstanceOf(ResponseStatusException.class).satisfies(e->assertThat(((ResponseStatusException)e).getStatusCode().value()).isEqualTo(409));
        assertThat(room.getTotalRooms()).isEqualTo(10);
    }
    @Test void unmanagedLegacyUpdateKeepsExistingContract() {
        RoomType room=room();service.requireLegacyUpdateAllowed(room,11);assertThat(room.getTotalRooms()).isEqualTo(10);
    }
    @Test void outOfBandCountInvalidatesApprovedVerificationWithoutRewritingHistory() {
        room();when(jdbc.queryForList("SELECT * FROM room_inventory_review_states WHERE room_id=?",roomId)).thenReturn(List.of(Map.of("revision",3L,"latest_request_id",requestId,"approved_request_id",requestId)));
        when(jdbc.queryForList("SELECT r.*,s.revision FROM room_inventory_review_requests r JOIN room_inventory_review_states s ON s.room_id=r.room_id WHERE r.id=?",requestId)).thenReturn(List.of(Map.of("proposed_quantity",3)));
        assertThat(service.state(roomId,actor).verified()).isFalse();verify(rooms,never()).saveAndFlush(any());
    }
    @Test void queueRejectsInvalidPaginationAndNonAdminBeforeStorage() {
        assertThatThrownBy(()->service.queue(actor,0,false)).isInstanceOf(SecurityException.class);
        assertThatThrownBy(()->service.queue(new RoomInventoryReviewService.Actor(owner,"ADMIN"),-1,false)).isInstanceOf(IllegalArgumentException.class);
        verifyNoInteractions(jdbc);
    }
}

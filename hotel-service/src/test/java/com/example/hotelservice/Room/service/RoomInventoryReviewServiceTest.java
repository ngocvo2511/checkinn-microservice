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
    @Test void unmanagedLegacyQuantityChangeAlsoRequiresReview() {
        RoomType room=room();assertThatThrownBy(()->service.requireLegacyUpdateAllowed(room,11)).isInstanceOf(ResponseStatusException.class);assertThat(room.getTotalRooms()).isEqualTo(10);
    }
    @Test void outOfBandCountInvalidatesApprovedVerificationWithoutRewritingHistory() {
        room();when(jdbc.queryForList("SELECT * FROM room_inventory_review_states WHERE room_id=?",roomId)).thenReturn(List.of(Map.of("revision",3L,"latest_request_id",requestId,"approved_request_id",requestId)));
        var approved=evidence();approved.put("proposed_quantity",3);
        when(jdbc.queryForList("SELECT r.*,s.revision FROM room_inventory_review_requests r JOIN room_inventory_review_states s ON s.room_id=r.room_id WHERE r.id=?",requestId)).thenReturn(List.of(approved));
        assertThat(service.state(roomId,actor).verified()).isFalse();verify(rooms,never()).saveAndFlush(any());
    }
    Map<String,Object> evidence() {
        var row=new HashMap<String,Object>();
        row.put("room_id",roomId);row.put("hotel_id",hotelId);row.put("proposed_quantity",10);
        row.put("status","APPROVED");row.put("source","Physical inventory record");
        row.put("requested_by",owner);row.put("reviewed_by",UUID.randomUUID());
        row.put("reviewed_at",java.time.Instant.now());row.put("review_reason","Record checked");
        return row;
    }
    void reviewedState(Map<String,Object> approved) {
        when(rooms.hasEffectiveInventoryApproval(roomId)).thenReturn(true);
        when(jdbc.queryForList("SELECT * FROM room_inventory_review_states WHERE room_id=?",roomId))
                .thenReturn(List.of(Map.of("revision",3L,"approved_request_id",requestId)));
        when(jdbc.queryForList("SELECT r.*,s.revision FROM room_inventory_review_requests r JOIN room_inventory_review_states s ON s.room_id=r.room_id WHERE r.id=?",requestId))
                .thenReturn(List.of(approved));
    }
    @Test void matchingApprovedTenWithEvidenceIsVerifiedWithoutMutation() {
        room();reviewedState(evidence());
        assertThat(service.state(roomId,actor).verified()).isTrue();
        verify(jdbc,never()).update(anyString(),any(Object[].class));
    }
    @Test void pendingRejectedAndIncompleteEvidenceNeverBecomeVerifiedMerelyByMatchingQuantity() {
        room();
        for(String status:List.of("PENDING","REJECTED","SUPERSEDED")) {
            var row=evidence();row.put("status",status);reviewedState(row);
            assertThat(service.state(roomId,actor).verified()).as(status).isFalse();
        }
        for(String field:List.of("requested_by","reviewed_by","reviewed_at","source","review_reason")) {
            var row=evidence();row.remove(field);reviewedState(row);
            assertThat(service.state(roomId,actor).verified()).as(field).isFalse();
        }
    }
    @Test void crossRoomAndCrossHotelPointersFailBeforeReturningPrivateEvidence() {
        room();
        for(String field:List.of("room_id","hotel_id")) {
            var row=evidence();row.put(field,UUID.randomUUID());reviewedState(row);
            assertThatThrownBy(()->service.state(roomId,actor)).isInstanceOf(ResponseStatusException.class)
                    .satisfies(e->assertThat(((ResponseStatusException)e).getStatusCode().value()).isEqualTo(409));
        }
    }
    @Test void rejectingCrossHotelRequestFailsWithoutRewritingItsAudit() {
        room();
        when(jdbc.queryForList("SELECT room_id FROM room_inventory_review_requests WHERE id=?",UUID.class,requestId)).thenReturn(List.of(roomId));
        when(jdbc.queryForList("SELECT * FROM room_inventory_review_states WHERE room_id=?",roomId))
                .thenReturn(List.of(Map.of("revision",1L,"latest_request_id",requestId)));
        var request=evidence();request.put("hotel_id",UUID.randomUUID());request.put("status","PENDING");
        when(jdbc.queryForList("SELECT r.*,s.revision FROM room_inventory_review_requests r JOIN room_inventory_review_states s ON s.room_id=r.room_id WHERE r.id=?",requestId)).thenReturn(List.of(request));
        assertThatThrownBy(()->service.review(requestId,new RoomInventoryReviewService.Review(false,1L,"Rejected"),new RoomInventoryReviewService.Actor(UUID.randomUUID(),"ADMIN")))
                .isInstanceOf(ResponseStatusException.class);
        verify(jdbc,never()).update(anyString(),any(Object[].class));verify(rooms,never()).saveAndFlush(any());
    }
    @Test void invalidProposalDoesNotLockRoomOrWriteAudit() {
        for(var invalid:List.of(new RoomInventoryReviewService.Proposal(null,0L,"Source"),
                new RoomInventoryReviewService.Proposal(0,0L,"Source"),
                new RoomInventoryReviewService.Proposal(1,0L," "))) {
            assertThatThrownBy(()->service.propose(roomId,invalid,actor)).isInstanceOf(com.example.hotelservice.validation.InputValidation.InvalidInput.class);
        }
        verifyNoInteractions(jdbc,rooms,hotels);
    }
    @Test void proposalCannotSupersedeAnotherRoomsPrivateAudit() {
        room();
        when(jdbc.queryForList("SELECT * FROM room_inventory_review_states WHERE room_id=?",roomId))
                .thenReturn(List.of(Map.of("revision",1L,"latest_request_id",requestId)));
        var request=evidence();request.put("room_id",UUID.randomUUID());request.put("status","PENDING");
        when(jdbc.queryForList("SELECT r.*,s.revision FROM room_inventory_review_requests r JOIN room_inventory_review_states s ON s.room_id=r.room_id WHERE r.id=?",requestId)).thenReturn(List.of(request));
        assertThatThrownBy(()->service.propose(roomId,new RoomInventoryReviewService.Proposal(12,1L,"Source"),actor))
                .isInstanceOf(ResponseStatusException.class);
        verify(jdbc,never()).update("UPDATE room_inventory_review_requests SET status='SUPERSEDED' WHERE id=? AND status='PENDING'",requestId);
        verify(jdbc,never()).update("UPDATE room_inventory_review_states SET latest_request_id=?,revision=revision+1 WHERE room_id=?",requestId,roomId);
    }
    @Test void queueRejectsInvalidPaginationAndNonAdminBeforeStorage() {
        assertThatThrownBy(()->service.queue(actor,0,false)).isInstanceOf(SecurityException.class);
        assertThatThrownBy(()->service.queue(new RoomInventoryReviewService.Actor(owner,"ADMIN"),-1,false)).isInstanceOf(IllegalArgumentException.class);
        verifyNoInteractions(jdbc);
    }
}

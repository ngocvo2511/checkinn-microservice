package com.example.hotelservice.Hotel.service;
import com.example.hotelservice.Hotel.entity.*;
import com.example.hotelservice.Hotel.enums.*;
import com.example.hotelservice.Hotel.repository.*;
import com.example.hotelservice.Hotel.controller.AccommodationTypeController;
import com.example.hotelservice.Hotel.dto.request.*;
import com.example.hotelservice.exception.GlobalExceptionHandler;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.*;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class AccommodationTypeServiceTest {
    HotelRepository hotels=mock(HotelRepository.class);
    AccommodationTypeChangeRepository changes=mock(AccommodationTypeChangeRepository.class);
    AccommodationTypeService service=new AccommodationTypeService(hotels,changes);
    UUID id=UUID.randomUUID(), owner=UUID.randomUUID(), admin=UUID.randomUUID();
    Hotel hotel;
    @BeforeEach void setup() {
        hotel=Hotel.builder().id(id).ownerId(owner).approvedStatus(HotelApprovalStatus.APPROVED).isActive(true).accommodationType(AccommodationType.HOTEL).build();
        when(hotels.findForUpdate(id)).thenReturn(Optional.of(hotel));
        when(hotels.findById(id)).thenReturn(Optional.of(hotel));
        when(changes.save(any())).thenAnswer(i -> i.getArgument(0));
    }
    AccommodationTypeChange proposal() { return service.propose(id,AccommodationType.VILLA,owner); }
    void stored(AccommodationTypeChange c) {
        when(changes.hotelIdFor(c.getId())).thenReturn(Optional.of(id));
        when(changes.findForUpdate(c.getId())).thenReturn(Optional.of(c));
        when(changes.findByHotelIdAndStatus(id,AccommodationChangeStatus.PENDING)).thenReturn(Optional.of(c));
    }
    @Test void approvedProposalKeepsPublicTypeAndListingAndRetriesReuseRequest() {
        var c=proposal(); stored(c);
        assertSame(c,proposal()); assertEquals(AccommodationType.HOTEL,hotel.getAccommodationType());
        assertTrue(hotel.getIsActive());assertEquals(HotelApprovalStatus.APPROVED,hotel.getApprovedStatus());verify(changes,times(1)).save(any());
    }
    @Test void approveAppliesOnceAndDoesNotReplayAfterAnotherChange() {
        var c=proposal();stored(c);service.review(c.getId(),true,null,admin,true);
        assertEquals(AccommodationType.VILLA,hotel.getAccommodationType());
        hotel.setAccommodationType(AccommodationType.APARTMENT);
        assertSame(c,service.review(c.getId(),true,null,admin,true));
        assertEquals(AccommodationType.APARTMENT,hotel.getAccommodationType());
        assertThrows(ResponseStatusException.class,()->service.review(c.getId(),false,"no",admin,true));
    }
    @Test void rejectionKeepsPublishedTypeAndRequiresReason() {
        var c=proposal();stored(c);
        assertThrows(IllegalArgumentException.class,()->service.review(c.getId(),false," ",admin,true));
        service.review(c.getId(),false,"Wrong classification",admin,true);
        assertEquals(AccommodationType.HOTEL,hotel.getAccommodationType());assertTrue(hotel.getIsActive());assertEquals(AccommodationChangeStatus.REJECTED,c.getStatus());
        assertSame(c,service.review(c.getId(),false,"Wrong classification",admin,true));
    }
    @Test void replacementSupersedesOldAndOldApprovalConflicts() {
        var c=proposal();stored(c);var next=service.propose(id,AccommodationType.APARTMENT,owner);
        assertEquals(AccommodationChangeStatus.SUPERSEDED,c.getStatus());assertNotEquals(c.getId(),next.getId());
        var ex=assertThrows(ResponseStatusException.class,()->service.review(c.getId(),true,null,admin,true));assertEquals(409,ex.getStatusCode().value());
        verify(changes).saveAndFlush(c);
    }
    @Test void staleSnapshotOrHotelStateIsNeverApplied() {
        var c=proposal();stored(c);hotel.setAccommodationType(AccommodationType.GUEST_HOUSE);
        assertThrows(ResponseStatusException.class,()->service.review(c.getId(),true,null,admin,true));
        hotel.setAccommodationType(AccommodationType.HOTEL);hotel.setApprovedStatus(HotelApprovalStatus.REJECTED);
        assertThrows(ResponseStatusException.class,()->service.review(c.getId(),true,null,admin,true));
        assertEquals(AccommodationChangeStatus.PENDING,c.getStatus());
    }
    @Test void selectCurrentCancelsPendingAndDraftOrLegacyCanBeClassified() {
        var c=proposal();stored(c);assertNull(service.propose(id,AccommodationType.HOTEL,owner));assertEquals(AccommodationChangeStatus.SUPERSEDED,c.getStatus());
        when(changes.findByHotelIdAndStatus(id,AccommodationChangeStatus.PENDING)).thenReturn(Optional.empty());
        hotel.setApprovedStatus(HotelApprovalStatus.DRAFT);hotel.setAccommodationType(null);
        assertNull(service.propose(id,AccommodationType.GUEST_HOUSE,owner));assertEquals(AccommodationType.GUEST_HOUSE,hotel.getAccommodationType());
    }
    @Test void permissionsAreCheckedBeforeReviewAndHistory() {
        assertThrows(AccessDeniedException.class,()->service.propose(id,AccommodationType.VILLA,UUID.randomUUID()));
        assertThrows(AccessDeniedException.class,()->service.history(id,UUID.randomUUID(),false));
        assertThrows(AccessDeniedException.class,()->service.review(UUID.randomUUID(),true,null,owner,false));
        assertThrows(AccessDeniedException.class,()->service.pending(false));
        verifyNoInteractions(changes);
    }
    @Test void taxonomyHasOnlyConfirmedOptionsAndStrictJsonCodes() throws Exception {
        var mvc=MockMvcBuilders.standaloneSetup(new AccommodationTypeController(service)).setControllerAdvice(new GlobalExceptionHandler()).build();
        mvc.perform(get("/hotels/accommodation-types")).andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(4)).andExpect(jsonPath("$[3].label").value("Guest House"));
        var om=new ObjectMapper();
        for (String value:List.of("\"\"","\"OTHER\"","123","true","{}","[]")) {
            assertThrows(Exception.class,()->om.readValue("{\"accommodationType\":"+value+"}",HotelUpdateRequest.class));
        }
        assertNull(om.readValue("{}",HotelUpdateRequest.class).accommodationType());
        assertNull(om.readValue("{\"accommodationType\":null}",HotelUpdateRequest.class).accommodationType());
    }
}

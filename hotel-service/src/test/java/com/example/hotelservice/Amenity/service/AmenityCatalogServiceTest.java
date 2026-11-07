package com.example.hotelservice.Amenity.service;

import com.example.hotelservice.Amenity.service.AmenityCatalogService.*;
import com.example.hotelservice.Hotel.entity.Hotel;
import com.example.hotelservice.Hotel.repository.HotelRepository;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.server.ResponseStatusException;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class AmenityCatalogServiceTest {
    final JdbcTemplate jdbc=mock(JdbcTemplate.class);
    final HotelRepository hotels=mock(HotelRepository.class);
    final AmenityCatalogService service=new AmenityCatalogService(jdbc,hotels);
    final Actor admin=new Actor(UUID.randomUUID(),"ADMIN");
    @Test void onlyAdminCanWriteCatalogOrReadInactiveOrReview() {
        for(String role:List.of("OWNER","CUSTOMER","BOOKING_SERVICE")) {
            Actor actor=new Actor(UUID.randomUUID(),role);
            assertThrows(SecurityException.class,()->service.save(null,true,actor));
            assertThrows(SecurityException.class,()->service.catalog(true,actor));
            assertThrows(SecurityException.class,()->service.review(UUID.randomUUID(),null,actor));
        }
        verifyNoInteractions(jdbc,hotels);
    }
    @Test void foreignOwnerAndCustomerCannotReadOrProposeEvenKnowingIds() {
        UUID hotelId=UUID.randomUUID();
        var hotel=Hotel.builder().id(hotelId).ownerId(UUID.randomUUID()).build();
        when(hotels.findById(hotelId)).thenReturn(Optional.of(hotel));
        when(hotels.findForUpdate(hotelId)).thenReturn(Optional.of(hotel));
        for(String role:List.of("OWNER","CUSTOMER")) {
            Actor actor=new Actor(UUID.randomUUID(),role);
            assertThrows(SecurityException.class,()->service.mappings(hotelId,actor));
            assertThrows(SecurityException.class,()->service.propose(hotelId,UUID.randomUUID(),null,actor));
        }
        verifyNoInteractions(jdbc);
    }
    @Test void invalidCatalogFieldsNeverReachDatabase() {
        for(var input:List.of(new CatalogInput("wifi","Wi-Fi",null,true,0L),
                new CatalogInput("WIFI"," ",null,true,0L),new CatalogInput("WIFI","x".repeat(256),null,true,0L),
                new CatalogInput("WIFI","Wi-Fi",null,null,0L),new CatalogInput("WIFI","Wi-Fi",null,true,-1L)))
            assertThrows(IllegalArgumentException.class,()->service.save(input,true,admin));
        verifyNoInteractions(jdbc);
    }
    @Test void unknownOrInactiveCodeFailsRatherThanBecomingAnEmptySearch() {
        when(jdbc.queryForObject(anyString(),eq(Boolean.class),eq("WIFI_FREE"))).thenReturn(false);
        assertThrows(IllegalArgumentException.class,()->service.validateCodes(List.of("WIFI_FREE")));
        assertThrows(IllegalArgumentException.class,()->service.validateCodes(List.of("Wi-Fi miễn phí")));
        verify(jdbc,times(1)).queryForObject(anyString(),eq(Boolean.class),eq("WIFI_FREE"));
    }
    @Test void duplicateCreateAndStaleUpdateAreConflictWithoutOverwriting() {
        var input=new CatalogInput("WIFI","Wi-Fi",null,true,0L);
        assertEquals(409,assertThrows(ResponseStatusException.class,()->service.save(input,true,admin)).getStatusCode().value());
        assertEquals(409,assertThrows(ResponseStatusException.class,()->service.save(input,false,admin)).getStatusCode().value());
    }
    @Test void noCodeFilterDoesNotQueryOrInferLegacyLabels() {
        assertTrue(service.matches(UUID.randomUUID(),List.of()));
        assertTrue(service.matches(UUID.randomUUID(),null));
        verifyNoInteractions(jdbc);
    }
}

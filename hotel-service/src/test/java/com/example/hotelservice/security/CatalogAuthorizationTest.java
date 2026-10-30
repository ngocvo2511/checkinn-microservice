package com.example.hotelservice.security;

import com.example.hotelservice.City.controller.CityController;
import com.example.hotelservice.City.mapper.CityMapper;
import com.example.hotelservice.City.service.CityService;
import com.example.hotelservice.City.service.LocationService;
import com.example.hotelservice.Hotel.controller.HotelController;
import com.example.hotelservice.Hotel.mapper.HotelMapper;
import com.example.hotelservice.Hotel.service.HotelService;
import com.example.hotelservice.Room.controller.RoomTypeController;
import com.example.hotelservice.Room.mapper.RoomTypeMapper;
import com.example.hotelservice.Room.service.RoomTypeService;
import com.example.hotelservice.config.SecurityConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(value = {CityController.class, HotelController.class, RoomTypeController.class,
        com.example.hotelservice.Room.availability.controller.AvailabilityController.class}, properties = {
        "spring.cloud.config.enabled=false", "spring.cloud.discovery.enabled=false",
        "spring.autoconfigure.exclude=org.springframework.boot.autoconfigure.security.servlet.UserDetailsServiceAutoConfiguration"})
@Import(SecurityConfig.class)
class CatalogAuthorizationTest {
    private static final UUID TARGET = UUID.fromString("00000000-0000-0000-0000-000000000011");
    private static final UUID ACTOR = UUID.fromString("00000000-0000-0000-0000-000000000012");
    @Autowired MockMvc mvc;
    @MockBean JwtDecoder decoder;
    @MockBean TokenRevocationService revocation;
    @MockBean CityService cities;
    @MockBean CityMapper cityMapper;
    @MockBean LocationService locations;
    @MockBean HotelService hotels;
    @MockBean HotelMapper hotelMapper;
    @MockBean RoomTypeService rooms;
    @MockBean RoomTypeMapper roomMapper;
    @MockBean com.example.hotelservice.Room.availability.service.AvailabilityService inventory;

    @BeforeEach void setUp() {
        when(decoder.decode(anyString())).thenAnswer(invocation -> {
            String token = invocation.getArgument(0);
            return Jwt.withTokenValue(token).header("alg", "HS256")
                    .subject("service".equals(token) ? "booking-service" : ACTOR.toString())
                    .claim("role", "admin".equals(token) ? "ADMIN" : (token.contains("service") ? "BOOKING_SERVICE" : "OWNER")).build();
        });
        when(cities.getAllCities()).thenReturn(List.of());
    }

    @Test void anonymousCannotDeleteCity() throws Exception {
        mvc.perform(delete("/cities/" + TARGET)).andExpect(status().isUnauthorized());
        verifyNoInteractions(cities);
    }
    @Test void ownerCannotDeleteCityByForgingAdminHeader() throws Exception {
        mvc.perform(delete("/cities/" + TARGET).header("Authorization", "Bearer owner").header("X-Is-Admin", "true"))
                .andExpect(status().isForbidden());
        verifyNoInteractions(cities);
    }
    @Test void administratorCanDeleteCity() throws Exception {
        mvc.perform(delete("/cities/" + TARGET).header("Authorization", "Bearer admin")).andExpect(status().isNoContent());
        verify(cities).deleteCity(TARGET);
    }
    @Test void publicCityReadRemainsAvailable() throws Exception {
        mvc.perform(get("/cities")).andExpect(status().isOk());
    }
    @Test void anonymousCannotActivateHotel() throws Exception {
        mvc.perform(patch("/hotels/" + TARGET + "/activate")).andExpect(status().isUnauthorized());
        verifyNoInteractions(hotels);
    }
    @Test void ownerCannotDeactivateHotel() throws Exception {
        mvc.perform(put("/hotels/" + TARGET + "/deactivate").header("Authorization", "Bearer owner"))
                .andExpect(status().isForbidden());
        verifyNoInteractions(hotels);
    }
    @Test void administratorCanActivateHotel() throws Exception {
        mvc.perform(patch("/hotels/" + TARGET + "/activate").header("Authorization", "Bearer admin"))
                .andExpect(status().isOk());
        verify(hotels).activateHotel(TARGET);
    }
    @Test void anonymousOwnerHeaderCannotActivateRoom() throws Exception {
        mvc.perform(put("/api/v1/" + TARGET + "/activate").header("X-OWNER-ID", ACTOR))
                .andExpect(status().isUnauthorized());
        verifyNoInteractions(rooms);
    }
    @Test void roomActivationUsesSignedSubjectEvenWhenOwnerHeaderIsForged() throws Exception {
        mvc.perform(put("/api/v1/" + TARGET + "/activate").header("Authorization", "Bearer owner")
                        .header("X-OWNER-ID", TARGET)).andExpect(status().isOk());
        verify(rooms).activateRoomType(TARGET, ACTOR);
    }
    @Test void roomDeactivationWorksWithBearerAndNoIdentityHeader() throws Exception {
        mvc.perform(put("/api/v1/" + TARGET + "/deactivate").header("Authorization", "Bearer owner"))
                .andExpect(status().isOk());
        verify(rooms).deactivateRoomType(TARGET, ACTOR);
    }
    @Test void unrelatedOwnerGetsForbiddenFromServiceOwnershipCheck() throws Exception {
        doThrow(new SecurityException("Different hotel owner")).when(rooms).activateRoomType(TARGET, ACTOR);
        mvc.perform(put("/api/v1/" + TARGET + "/activate").header("Authorization", "Bearer owner")
                        .header("X-OWNER-ID", TARGET)).andExpect(status().isForbidden());
    }
    @Test void anonymousCannotMutateHoldByKnowingItsId() throws Exception {
        mvc.perform(post("/api/v1/availability/hold/" + TARGET + "/confirm")).andExpect(status().isUnauthorized());
        verifyNoInteractions(inventory);
    }
    @Test void ownerOrAdminJwtIsNotAnInventoryServiceIdentity() throws Exception {
        for (String token : List.of("owner", "admin", "forged-service")) {
            mvc.perform(post("/api/v1/availability/hold/" + TARGET + "/release")
                            .header("Authorization", "Bearer " + token).header("X-Service-Name", "booking-service"))
                    .andExpect(status().isForbidden());
        }
        verifyNoInteractions(inventory);
    }
    @Test void verifiedBookingServiceCanReadHold() throws Exception {
        mvc.perform(get("/api/v1/availability/hold/" + TARGET).header("Authorization", "Bearer service"))
                .andExpect(status().isOk());
        verify(inventory).getHold(TARGET);
    }
}

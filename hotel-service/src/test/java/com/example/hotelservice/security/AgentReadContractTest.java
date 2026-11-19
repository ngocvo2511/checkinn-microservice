package com.example.hotelservice.security;

import com.example.hotelservice.Amenity.controller.AmenityCatalogController;
import com.example.hotelservice.Amenity.controller.AmenityController;
import com.example.hotelservice.City.controller.CityController;
import com.example.hotelservice.Hotel.controller.AccommodationTypeController;
import com.example.hotelservice.Hotel.controller.HotelController;
import com.example.hotelservice.Hotel.controller.HotelOfferController;
import com.example.hotelservice.Policy.controller.PolicyController;
import com.example.hotelservice.Review.controller.ReviewController;
import com.example.hotelservice.Review.controller.ReviewSummaryController;
import com.example.hotelservice.Room.availability.controller.AvailabilityController;
import com.example.hotelservice.Room.availability.service.InventoryApprovalRequiredException;
import com.example.hotelservice.Room.controller.RoomTypeController;
import com.example.hotelservice.config.SecurityConfig;
import jakarta.persistence.EntityNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.hamcrest.Matchers.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Batch 9B — contract for the read-only hotel API surface an AI READ agent may call.
 * C1: public reads are anonymous and use public (published/approved) service methods; neighbouring private reads are denied.
 * C2: invalid search/availability input is rejected with 400 before any catalog query.
 * C3: every failure uses the stable error envelope without internal details.
 * Service-boundary evidence only (MockMvc + real SecurityConfig/advice); not Gateway or database acceptance.
 */
@WebMvcTest(value = {HotelController.class, HotelOfferController.class, RoomTypeController.class, AvailabilityController.class,
        AccommodationTypeController.class, AmenityCatalogController.class, AmenityController.class, PolicyController.class,
        CityController.class, ReviewSummaryController.class, ReviewController.class}, properties = {
        "spring.cloud.config.enabled=false", "spring.cloud.discovery.enabled=false",
        "spring.autoconfigure.exclude=org.springframework.boot.autoconfigure.security.servlet.UserDetailsServiceAutoConfiguration"})
@Import(SecurityConfig.class)
class AgentReadContractTest {
    private static final UUID TARGET = UUID.fromString("00000000-0000-0000-0000-000000000091");
    private static final UUID ACTOR = UUID.fromString("00000000-0000-0000-0000-000000000092");
    private static final String STAY = "checkIn=2030-01-10&checkOut=2030-01-12";

    @Autowired MockMvc mvc;
    @MockBean JwtDecoder decoder;
    @MockBean TokenRevocationService revocation;
    @MockBean com.example.hotelservice.Hotel.service.HotelService hotels;
    @MockBean com.example.hotelservice.Hotel.mapper.HotelMapper hotelMapper;
    @MockBean com.example.hotelservice.Hotel.service.HotelOfferSearchService offers;
    @MockBean com.example.hotelservice.Hotel.service.HotelOfferCandidates candidates;
    @MockBean com.example.hotelservice.Hotel.service.AccommodationTypeService accommodationTypes;
    @MockBean com.example.hotelservice.City.service.LocationService locations;
    @MockBean com.example.hotelservice.City.service.CityService cityService;
    @MockBean com.example.hotelservice.City.mapper.CityMapper cityMapper;
    @MockBean com.example.hotelservice.City.repository.CityRepository cityRepository;
    @MockBean com.example.hotelservice.Amenity.service.AmenityCatalogService amenityCatalog;
    @MockBean com.example.hotelservice.Amenity.service.AmenityCategoryService amenityCategories;
    @MockBean com.example.hotelservice.Policy.service.PolicyCategoryService policyCategories;
    @MockBean com.example.hotelservice.Review.service.ReviewSummaryService reviewSummaries;
    @MockBean com.example.hotelservice.Review.service.ReviewService reviews;
    @MockBean com.example.hotelservice.Room.service.RoomTypeService rooms;
    @MockBean com.example.hotelservice.Room.mapper.RoomTypeMapper roomMapper;
    @MockBean com.example.hotelservice.Room.availability.service.AvailabilityService inventory;

    @BeforeEach void tokens() {
        when(decoder.decode(anyString())).thenAnswer(invocation -> {
            String token = invocation.getArgument(0);
            String role = switch (token) { case "admin" -> "ADMIN"; case "owner" -> "OWNER"; default -> "CUSTOMER"; };
            return Jwt.withTokenValue(token).header("alg", "HS256").subject(ACTOR.toString()).claim("role", role).build();
        });
    }

    // ---------------------------------------------------------------- C1: public read surface
    static final List<String> PUBLIC_READS = List.of(
            "/hotels/offers/page?hotelName=Fixture&" + STAY,
            "/hotels/offers?hotelName=Fixture&" + STAY,
            "/hotels/" + TARGET,
            "/api/v1/hotel/" + TARGET,
            "/api/v1/" + TARGET,
            "/api/v1/availability/check?roomTypeId=" + TARGET + "&" + STAY + "&quantity=1",
            "/hotels/accommodation-types",
            "/amenity-catalog",
            "/hotels/amenities/categories",
            "/hotels/policies/categories",
            "/cities",
            "/cities/search/locations?query=Da",
            "/api/v1/reviews/summaries?hotelIds=" + TARGET,
            "/api/v1/reviews/stats/hotel/" + TARGET);

    @Test void everyAgentReadEndpointIsAnonymous() throws Exception {
        for (String url : PUBLIC_READS) mvc.perform(get(url)).andExpect(status().isOk());
    }

    @Test void publicReadsUsePublicPolicyEvenWithSignedOwnerAndForgedAdminHeaders() throws Exception {
        for (String url : List.of("/hotels/" + TARGET, "/api/v1/hotel/" + TARGET, "/api/v1/" + TARGET))
            mvc.perform(get(url).header("Authorization", "Bearer owner").header("X-Is-Admin", "true").header("X-User-Id", ACTOR))
                    .andExpect(status().isOk());
        verify(hotels).getDetail(TARGET);
        verify(rooms).getPublicByHotel(TARGET);
        verify(rooms).getPublicById(TARGET);
        verify(hotels, never()).getPreview(any(), any(), anyBoolean());
        verify(rooms, never()).getPreviewById(any(), any(), anyBoolean());
        verify(amenityCatalog, never()).catalog(eq(true), any());
    }

    static final List<String> PRIVATE_READS = List.of(
            "/hotels/" + TARGET + "/preview", "/api/v1/" + TARGET + "/preview", "/api/v1/hotel/" + TARGET + "/preview",
            "/hotels/" + TARGET + "/documents", "/hotels/owner", "/hotels/pending", "/hotels/pending/" + TARGET,
            "/hotels/" + TARGET + "/amenity-mappings", "/hotels/" + TARGET + "/accommodation-type-changes",
            "/hotels/accommodation-type-changes/pending", "/api/v1/" + TARGET + "/inventory-review",
            "/admin/inventory-reviews", "/admin/amenity-catalog", "/admin/amenity-mappings",
            "/internal/hotels/" + TARGET + "/owner", "/internal/reports/hotels/" + TARGET,
            "/api/v1/availability/hold/" + TARGET, "/medias/preview?targetId=" + TARGET,
            "/api/v1/reviews/me", "/api/v1/reviews/owner/all");

    @Test void neighbouringPrivateReadsRejectAnonymousCallersWithTheErrorEnvelope() throws Exception {
        for (String url : PRIVATE_READS)
            envelope(mvc.perform(get(url).header("X-Is-Admin", "true").header("X-User-Id", ACTOR)), 401, "UNAUTHORIZED")
                    .andExpect(header().string("WWW-Authenticate", "Bearer"));
        verifyNoInteractions(hotels, rooms, inventory, reviews, amenityCatalog, accommodationTypes);
    }

    @Test void customerTokenCannotReachAdminInternalOrHoldReads() throws Exception {
        for (String url : List.of("/hotels/pending", "/hotels/accommodation-type-changes/pending", "/admin/inventory-reviews",
                "/admin/amenity-catalog", "/admin/amenity-mappings", "/internal/hotels/" + TARGET + "/owner",
                "/internal/reports/hotels/" + TARGET, "/api/v1/availability/hold/" + TARGET))
            envelope(mvc.perform(get(url).header("Authorization", "Bearer customer").header("X-Is-Admin", "true")), 403, "FORBIDDEN");
        verifyNoInteractions(hotels, rooms, inventory, amenityCatalog, accommodationTypes);
    }

    // ---------------------------------------------------------------- success shapes an agent parses
    @Test void emptySearchIsASuccessfulPageNotAnError() throws Exception {
        when(hotels.searchByName("Nowhere")).thenReturn(List.of());
        mvc.perform(get("/hotels/offers/page?hotelName=Nowhere&" + STAY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(0))).andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.size").value(20)).andExpect(jsonPath("$.totalElements").value(0))
                .andExpect(jsonPath("$.totalPages").isNumber()).andExpect(jsonPath("$.sort").value("DEFAULT"))
                .andExpect(jsonPath("$.availableAmenities").isArray()).andExpect(jsonPath("$.code").doesNotExist());
    }

    @Test void availabilityCheckReturnsCountAndBooleanForTheRequestedQuantity() throws Exception {
        when(inventory.getAvailableRoomCount(TARGET, LocalDate.parse("2030-01-10"), LocalDate.parse("2030-01-12"))).thenReturn(1);
        mvc.perform(get("/api/v1/availability/check?roomTypeId=" + TARGET + "&" + STAY + "&quantity=2"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.available").value(false)).andExpect(jsonPath("$.availableRooms").value(1))
                .andExpect(jsonPath("$.checkedAt", matchesPattern("[0-9]{4}-[0-9]{2}-[0-9]{2}T.*Z")));
    }

    // ---------------------------------------------------------------- C2: invalid input, no query
    static final List<String> INVALID_SEARCHES = List.of(
            STAY,                                                     // no destination or hotel name
            "hotelName=Fixture&checkIn=2030-01-10&checkOut=2030-01-10", // zero-night stay
            "hotelName=Fixture&checkIn=2030-01-12&checkOut=2030-01-10", // reversed stay
            "hotelName=Fixture&checkIn=2030-01-10",                     // half a stay
            "hotelName=Fixture&checkIn=2030-13-01&checkOut=2030-13-03", // not an ISO date
            "hotelName=Fixture&rooms=0", "hotelName=Fixture&adults=0", "hotelName=Fixture&children=-1",
            "hotelName=Fixture&minPrice=-1", "hotelName=Fixture&minPrice=500&maxPrice=100",
            "hotelName=Fixture&accommodationTypes=NOT_A_TYPE", "hotelName=Fixture&amenityCodes=wifi",
            "cityId=not-a-uuid",
            "hotelName=Fixture&page=-1", "hotelName=Fixture&size=0", "hotelName=Fixture&size=51",
            "hotelName=Fixture&sort=CHEAPEST", "hotelName=Fixture&starRatings=6", "hotelName=Fixture&guestRatings=11",
            // F-9B-3 limits (business zone Asia/Ho_Chi_Minh): past check-in, >30 nights, rooms 1..20, adults 1..40, children 0..40
            "hotelName=Fixture&checkIn=2020-01-10&checkOut=2020-01-12",
            "hotelName=Fixture&checkIn=2030-01-01&checkOut=2030-02-01",
            "hotelName=Fixture&rooms=21", "hotelName=Fixture&adults=41", "hotelName=Fixture&children=41");

    @Test void invalidSearchCriteriaAre400BeforeAnyCatalogQuery() throws Exception {
        for (String query : INVALID_SEARCHES) {
            mvc.perform(get("/hotels/offers/page?" + query))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code", anyOf(is("INVALID_REQUEST"), is("VALIDATION_ERROR"))))
                    .andExpect(jsonPath("$.requestId", not(emptyOrNullString())));
        }
        verifyNoInteractions(hotels, candidates, offers, cityRepository, locations, reviewSummaries);
    }

    @Test void invalidAvailabilityParametersAre400WithoutReadingInventory() throws Exception {
        String base = "/api/v1/availability/check?";
        for (String query : List.of("roomTypeId=" + TARGET + "&" + STAY,                       // missing quantity
                "roomTypeId=" + TARGET + "&" + STAY + "&quantity=0",
                "roomTypeId=" + TARGET + "&" + STAY + "&quantity=-1",
                "roomTypeId=not-a-uuid&" + STAY + "&quantity=1",
                "roomTypeId=" + TARGET + "&checkIn=10/01/2030&checkOut=2030-01-12&quantity=1",
                "roomTypeId=" + TARGET + "&checkOut=2030-01-12&quantity=1",
                "roomTypeId=" + TARGET + "&" + STAY + "&quantity=21",                       // F-9B-3: at most 20 rooms
                "roomTypeId=" + TARGET + "&checkIn=2020-01-10&checkOut=2020-01-12&quantity=1", // past check-in
                "roomTypeId=" + TARGET + "&checkIn=2030-01-01&checkOut=2030-02-01&quantity=1", // 31 nights
                "roomTypeId=" + TARGET + "&checkIn=2030-01-12&checkOut=2030-01-10&quantity=1"))
            envelope(mvc.perform(get(base + query)), 400, null);
        verifyNoInteractions(inventory);
    }

    // ---------------------------------------------------------------- C3: failure envelope
    @Test void unapprovedInventoryIsAStableBusinessConflictNotSoldOut() throws Exception {
        when(inventory.getAvailableRoomCount(eq(TARGET), any(), any())).thenThrow(new InventoryApprovalRequiredException());
        when(rooms.getPublicById(TARGET)).thenThrow(new InventoryApprovalRequiredException());
        // F-9B-4 kept: the room read stays 409 because Booking admission maps it (HotelCatalogClient).
        for (String url : List.of("/api/v1/availability/check?roomTypeId=" + TARGET + "&" + STAY + "&quantity=1", "/api/v1/" + TARGET))
            envelope(mvc.perform(get(url)), 409, "INVENTORY_APPROVAL_REQUIRED")
                    .andExpect(content().string(not(containsString("reviewer"))));
    }

    @Test void serviceDateRejectionAndMissingResourcesMapToClientErrors() throws Exception {
        when(inventory.getAvailableRoomCount(eq(TARGET), any(), any())).thenThrow(new IllegalArgumentException("Ngày trả phải sau ngày nhận"));
        envelope(mvc.perform(get("/api/v1/availability/check?roomTypeId=" + TARGET + "&" + STAY + "&quantity=1")), 400, "INVALID_REQUEST")
                .andExpect(jsonPath("$.message").value("Ngày trả phải sau ngày nhận"));
        when(hotels.getDetail(TARGET)).thenThrow(new EntityNotFoundException("Không tìm thấy khách sạn"));
        envelope(mvc.perform(get("/hotels/" + TARGET)), 404, "NOT_FOUND");
        envelope(mvc.perform(get("/hotels/not-a-uuid")), 400, null);
    }

    @Test void unexpectedAndDatabaseFailuresAreSanitized5xxNeverEmptyResults() throws Exception {
        when(hotels.searchByName("Fixture")).thenThrow(new org.springframework.dao.DataAccessResourceFailureException("jdbc:postgresql://secret-host password=x"));
        when(rooms.getPublicByHotel(TARGET)).thenThrow(new RuntimeException("synthetic stack detail"));
        when(hotels.getDetail(TARGET)).thenThrow(new org.springframework.transaction.CannotCreateTransactionException("Could not open JPA EntityManager: secret-host"));
        // F-9B-1: database outages are 503 DEPENDENCY_UNAVAILABLE (retry later); unexpected bugs stay 500 INTERNAL_ERROR.
        for (var expected : List.of(List.of("/hotels/offers/page?hotelName=Fixture&" + STAY, "503", "DEPENDENCY_UNAVAILABLE"),
                List.of("/hotels/" + TARGET, "503", "DEPENDENCY_UNAVAILABLE"),
                List.of("/api/v1/hotel/" + TARGET, "500", "INTERNAL_ERROR")))
            mvc.perform(get(expected.get(0))).andExpect(status().is(Integer.parseInt(expected.get(1))))
                    .andExpect(jsonPath("$.code").value(expected.get(2)))
                    .andExpect(jsonPath("$.requestId", not(emptyOrNullString())))
                    .andExpect(jsonPath("$.content").doesNotExist())
                    .andExpect(content().string(not(anyOf(containsString("secret-host"), containsString("password"),
                            containsString("synthetic stack detail"), containsString("Exception")))));
    }

    private ResultActions envelope(ResultActions result, int status, String code) throws Exception {
        result.andExpect(status().is(status))
                .andExpect(jsonPath("$.status").value(status))
                .andExpect(jsonPath("$.code", code == null ? not(emptyOrNullString()) : is(code)))
                .andExpect(jsonPath("$.message", not(emptyOrNullString())))
                .andExpect(jsonPath("$.path", org.hamcrest.Matchers.startsWith("/")))
                .andExpect(jsonPath("$.requestId", not(emptyOrNullString())))
                .andExpect(jsonPath("$.trace").doesNotExist())
                .andExpect(jsonPath("$.exception").doesNotExist());
        return result;
    }
}

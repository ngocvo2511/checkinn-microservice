package com.example.bookingservice.booking.controller;

import static com.example.bookingservice.contract.TestIds.*;
import com.example.bookingservice.booking.dto.BookingResponse;
import com.example.bookingservice.booking.dto.CreateBookingRequest;
import com.example.bookingservice.booking.entity.Booking;
import com.example.bookingservice.booking.repository.BookingRepository;
import com.example.bookingservice.booking.service.BookingService;
import com.example.bookingservice.config.SecurityConfig;
import com.example.bookingservice.payment.controller.PaymentController;
import com.example.bookingservice.payment.entity.Payment;
import com.example.bookingservice.payment.repository.PaymentRepository;
import com.example.bookingservice.payment.service.PaymentService;
import com.example.bookingservice.payment.vnpay.VnPayProperties;
import com.example.bookingservice.security.BookingAccess;
import com.example.bookingservice.security.JwtFilter;
import com.example.bookingservice.security.JwtService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Batch 9C — HTTP contract for the routes an AI WRITE agent may call on a user's behalf
 * (create/read/cancel booking, read payment, obtain a VNPay URL) and the routes it must never reach.
 * Service semantics (stale price, idempotent replay, key reuse, hold timeout, compensation) are covered by
 * BookingServiceTest/BookingIdempotencyTest; this class pins what the agent observes over HTTP.
 * Service-boundary evidence only (MockMvc + real SecurityConfig/JwtFilter/BookingAccess/advice).
 */
@WebMvcTest(value = {BookingController.class, PaymentController.class,
        com.example.bookingservice.payment.refund.RefundRequestController.class,
        com.example.bookingservice.payment.refund.ManualRefundController.class}, properties = {
        "spring.cloud.config.enabled=false", "spring.cloud.discovery.enabled=false",
        "spring.autoconfigure.exclude=org.springframework.boot.autoconfigure.security.servlet.UserDetailsServiceAutoConfiguration"})
@Import({SecurityConfig.class, JwtFilter.class, BookingAccess.class, com.example.bookingservice.booking.service.CancellationPolicy.class,
        com.example.bookingservice.config.ApiJsonConfig.class})
class AgentWriteContractTest {
    private static final UUID CUSTOMER = UUID.fromString("00000000-0000-0000-0000-000000000091");
    private static final UUID OTHER = UUID.fromString("00000000-0000-0000-0000-000000000092");
    private static final String KEY = "6f1c2a7e-3b4d-4c5e-8f60-718293a4b5c6";

    @Autowired MockMvc mvc;
    @MockBean BookingService service;
    @MockBean JwtService jwt;
    @MockBean BookingRepository bookings;
    @MockBean PaymentRepository payments;
    @MockBean PaymentService paymentService;
    @MockBean VnPayProperties vnPayProperties;
    @MockBean RestTemplate hotel;
    @MockBean com.example.bookingservice.payment.refund.RefundRequestService refunds;
    @MockBean com.example.bookingservice.payment.refund.ManualRefundService manualRefunds;

    @BeforeEach void setUp() {
        when(jwt.generateInventoryServiceToken()).thenReturn("synthetic-service-token");
        when(jwt.validateToken(anyString())).thenReturn(true);
        when(jwt.extractUserId("customer")).thenReturn(CUSTOMER);
        when(jwt.extractUserId("other")).thenReturn(OTHER);
        when(jwt.extractRole(anyString())).thenReturn("CUSTOMER");
        when(bookings.findById(id("agent-booking"))).thenReturn(Optional.of(Booking.builder()
                .id(id("agent-booking")).userId(CUSTOMER).hotelId(id("agent-hotel")).build()));
        when(payments.findById(id("agent-payment"))).thenReturn(Optional.of(Payment.builder()
                .id(id("agent-payment")).bookingId(id("agent-booking")).build()));
    }

    // ---------------------------------------------------------------- C1: agent-usable routes
    private List<MockHttpServletRequestBuilder> agentRoutes() {
        String booking = wire("agent-booking");
        return List.of(post("/api/bookings").contentType("application/json").content(body(null)),
                get("/api/bookings/" + booking), get("/api/bookings/user/" + CUSTOMER), delete("/api/bookings/" + booking),
                get("/api/payments/booking/" + booking), post("/api/payments/vnpay/create").param("bookingId", booking),
                post("/api/payments/" + wire("agent-payment") + "/refund-request").contentType("application/json").content("{\"reason\":\"synthetic\"}"));
    }

    @Test void everyAgentWriteRouteRequiresASignedUserAndIgnoresIdentityHeaders() throws Exception {
        for (var route : agentRoutes())
            envelope(mvc.perform(route.header("X-User-Id", CUSTOMER).header("X-Is-Admin", "true").header("X-Role", "ADMIN")), 401, "UNAUTHORIZED");
        verifyNoInteractions(service, paymentService, refunds);
    }

    @Test void anotherCustomerCannotActOnTheBookingOrPayment() throws Exception {
        for (var route : agentRoutes().subList(1, agentRoutes().size()))
            envelope(mvc.perform(route.header("Authorization", "Bearer other").header("X-User-Id", CUSTOMER)), 403, "FORBIDDEN");
        verifyNoInteractions(service, paymentService, refunds);
    }

    @Test void routesOutsideTheAgentScopeAreForbiddenToCustomers() throws Exception {
        String booking = wire("agent-booking"), payment = wire("agent-payment");
        for (var route : List.<MockHttpServletRequestBuilder>of(
                put("/api/bookings/" + booking + "/status").param("status", "CONFIRMED"),
                post("/api/bookings/" + booking + "/cancel-reviewed").contentType("application/json").content("{\"reason\":\"x\"}"),
                get("/api/bookings/count/total"), get("/api/bookings/count/today"),
                get("/api/bookings/hotel/" + wire("agent-hotel")),
                post("/api/payments/" + payment + "/refund"),
                post("/api/payments/" + payment + "/refund-request/approve"),
                post("/api/payments/" + payment + "/refund-request/reject"),
                post("/api/payments/" + booking + "/confirm"),
                get("/api/payments/manual-refunds")))
            mvc.perform(route.header("Authorization", "Bearer customer").header("X-Is-Admin", "true"))
                    .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("FORBIDDEN"));
        verify(service, never()).updateBookingStatus(any(), any());
        verifyNoInteractions(paymentService, refunds, manualRefunds);
    }

    // ---------------------------------------------------------------- C2/C4: create contract
    @Test void createBindsPrincipalAndForwardsTheSameIdempotencyKey() throws Exception {
        when(service.createBooking(any())).thenAnswer(i -> BookingResponse.builder().id(id("agent-booking"))
                .userId(UUID.fromString(i.<CreateBookingRequest>getArgument(0).getUserId())).build());
        for (int attempt = 0; attempt < 2; attempt++)
            mvc.perform(post("/api/bookings").header("Authorization", "Bearer customer").contentType("application/json").content(body(KEY)))
                    .andExpect(status().isCreated()).andExpect(jsonPath("$.id").value(wire("agent-booking")))
                    .andExpect(jsonPath("$.userId").value(CUSTOMER.toString()));
        var captor = ArgumentCaptor.forClass(CreateBookingRequest.class);
        verify(service, times(2)).createBooking(captor.capture());
        assertThat(captor.getAllValues()).allSatisfy(r -> {
            assertThat(r.getUserId()).isEqualTo(CUSTOMER.toString());
            assertThat(r.getIdempotencyKey()).isEqualTo(KEY);
        });
    }

    @Test void malformedCreateBodiesAre400WithFieldErrorsAndNoBusinessEffect() throws Exception {
        // Production createBooking starts with InputValidation.require; reproduce only that first step here.
        // BookingServiceTest proves the real service performs no catalog/hold/repository work after it fails.
        when(service.createBooking(any())).thenAnswer(i -> {
            com.example.bookingservice.validation.InputValidation.require(i.<CreateBookingRequest>getArgument(0));
            throw new AssertionError("invalid input must not pass validation");
        });
        for (String bad : List.of("{", "{}", body(KEY).replace("\"adults\":2", "\"adults\":1.5"),
                body(KEY).replace("11111111-1111-4111-8111-111111111111", "not-a-uuid"),
                body(KEY).replace("synthetic@example.invalid", "not-an-email"),
                body(KEY).replace("\"quantity\":1", "\"quantity\":0")))
            envelope(mvc.perform(post("/api/bookings").header("Authorization", "Bearer customer").contentType("application/json").content(bad)), 400, null)
                    .andExpect(jsonPath("$.fieldErrors").isMap());
        verifyNoInteractions(paymentService, refunds);
    }

    @Test void businessRejectionsKeepDistinctStableCodes() throws Exception {
        var create = post("/api/bookings").header("Authorization", "Bearer customer").contentType("application/json").content(body(KEY));
        when(service.createBooking(any())).thenThrow(new IllegalArgumentException("Room price has changed; refresh the offer before booking"));
        envelope(mvc.perform(create), 400, "INVALID_REQUEST").andExpect(jsonPath("$.message").value(containsString("price has changed")));
        reset(service);
        when(service.createBooking(any())).thenThrow(new ResponseStatusException(HttpStatus.CONFLICT, "Idempotency key was already used for different booking input"));
        envelope(mvc.perform(create), 409, "CONFLICT").andExpect(jsonPath("$.message").value(containsString("Idempotency key")));
        reset(service);
        when(service.createBooking(any())).thenThrow(new ResponseStatusException(HttpStatus.CONFLICT, "INVENTORY_APPROVAL_REQUIRED"));
        envelope(mvc.perform(create), 409, "CONFLICT");
        reset(service);
        when(service.createBooking(any())).thenThrow(new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Hotel inventory unavailable"));
        envelope(mvc.perform(create), 503, "DEPENDENCY_UNAVAILABLE").andExpect(jsonPath("$.message").value("Service temporarily unavailable"));
        reset(service);
        when(service.createBooking(any())).thenThrow(new RuntimeException("jdbc:postgresql://secret-host password=x"));
        envelope(mvc.perform(create), 500, "INTERNAL_ERROR").andExpect(content().string(not(containsString("secret-host"))));
    }

    // ---------------------------------------------------------------- cancellation and payment handoff
    @Test void cancellationAndPolicyAreObservableBeforeAndAfterTheMutation() throws Exception {
        mvc.perform(get("/api/bookings/cancellation-policy")).andExpect(status().isOk());
        mvc.perform(delete("/api/bookings/" + wire("agent-booking")).header("Authorization", "Bearer customer")).andExpect(status().isNoContent());
        verify(service).cancelBooking(id("agent-booking"), CUSTOMER);
        doThrow(new ResponseStatusException(HttpStatus.CONFLICT, "Booking cannot be cancelled in its current state"))
                .when(service).cancelBooking(id("agent-booking"), CUSTOMER);
        envelope(mvc.perform(delete("/api/bookings/" + wire("agent-booking")).header("Authorization", "Bearer customer")), 409, "CONFLICT");
    }

    @Test void rejectedCancellationReturnsTheStandardEnvelopeWithoutTheExceptionText() throws Exception {
        doThrow(new IllegalArgumentException("synthetic internal detail")).when(service).cancelBooking(id("agent-booking"), CUSTOMER);
        envelope(mvc.perform(delete("/api/bookings/" + wire("agent-booking")).header("Authorization", "Bearer customer")), 400, "INVALID_REQUEST")
                .andExpect(jsonPath("$.message").value("Booking cannot be cancelled in its current state"))
                .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("synthetic"))));
    }

    @Test void databaseOutageIsA503DependencyFailureNotAnInternalError() throws Exception {
        when(service.createBooking(any())).thenThrow(new org.springframework.transaction.CannotCreateTransactionException("Could not open JPA EntityManager: secret-host"));
        when(service.getBooking(id("agent-booking"))).thenThrow(new org.springframework.dao.DataAccessResourceFailureException("jdbc:postgresql://secret-host"));
        for (ResultActions result : List.of(
                mvc.perform(post("/api/bookings").header("Authorization", "Bearer customer").contentType("application/json").content(body(KEY))),
                mvc.perform(get("/api/bookings/" + wire("agent-booking")).header("Authorization", "Bearer customer"))))
            envelope(result, 503, "DEPENDENCY_UNAVAILABLE")
                    .andExpect(content().string(not(containsString("secret-host"))));
    }

    @Test void rejectedVnPayCreationReturnsTheStandardEnvelopeWithoutTheExceptionText() throws Exception {
        when(paymentService.initVnPayPayment(eq(id("agent-booking")), any())).thenThrow(new IllegalArgumentException("synthetic internal detail"));
        envelope(mvc.perform(post("/api/payments/vnpay/create").param("bookingId", wire("agent-booking")).header("Authorization", "Bearer customer")), 400, "INVALID_REQUEST")
                .andExpect(jsonPath("$.message").value("Payment cannot be created for this booking"))
                .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("synthetic"))));
    }

    @Test void paymentHandoffReturnsAProviderUrlOnlyForTheOwner() throws Exception {
        when(paymentService.initVnPayPayment(eq(id("agent-booking")), any())).thenReturn(null);
        mvc.perform(post("/api/payments/vnpay/create").param("bookingId", wire("agent-booking")).header("Authorization", "Bearer customer"))
                .andExpect(status().isOk());
        verify(paymentService).initVnPayPayment(eq(id("agent-booking")), any());
        envelope(mvc.perform(post("/api/payments/vnpay/create").param("bookingId", "not-a-uuid").header("Authorization", "Bearer customer")), 400, null);
    }

    private static String body(String key) {
        return "{\"hotelId\":\"11111111-1111-4111-8111-111111111111\",\"checkInDate\":\"2099-01-01\",\"checkOutDate\":\"2099-01-02\","
                + "\"adults\":2,\"children\":0,\"contactName\":\"Synthetic guest\",\"contactEmail\":\"synthetic@example.invalid\",\"contactPhone\":\"0000000000\","
                + "\"items\":[{\"roomTypeId\":\"22222222-2222-4222-8222-222222222222\",\"checkInDate\":\"2099-01-01\",\"checkOutDate\":\"2099-01-02\",\"quantity\":1,\"unitPrice\":1000000}]"
                + (key == null ? "" : ",\"idempotencyKey\":\"" + key + "\"") + "}";
    }

    private ResultActions envelope(ResultActions result, int status, String code) throws Exception {
        return result.andExpect(status().is(status))
                .andExpect(jsonPath("$.status").value(status))
                .andExpect(jsonPath("$.code", code == null ? not(emptyOrNullString()) : is(code)))
                .andExpect(jsonPath("$.message", not(emptyOrNullString())))
                .andExpect(jsonPath("$.requestId", not(emptyOrNullString())))
                .andExpect(jsonPath("$.trace").doesNotExist());
    }
}

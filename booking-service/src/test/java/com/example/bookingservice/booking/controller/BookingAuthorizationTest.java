package com.example.bookingservice.booking.controller;

import com.example.bookingservice.booking.dto.BookingResponse;
import com.example.bookingservice.booking.entity.Booking;
import com.example.bookingservice.booking.repository.BookingRepository;
import com.example.bookingservice.booking.service.BookingService;
import com.example.bookingservice.config.SecurityConfig;
import com.example.bookingservice.security.JwtFilter;
import com.example.bookingservice.security.JwtService;
import com.example.bookingservice.security.BookingAccess;
import com.example.bookingservice.payment.repository.PaymentRepository;
import com.example.bookingservice.payment.controller.PaymentController;
import com.example.bookingservice.payment.entity.Payment;
import com.example.bookingservice.payment.service.PaymentService;
import com.example.bookingservice.payment.vnpay.VnPayProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.client.RestTemplate;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(value = {BookingController.class, PaymentController.class, com.example.bookingservice.payment.refund.RefundRequestController.class}, properties = {
        "spring.cloud.config.enabled=false", "spring.cloud.discovery.enabled=false",
        "spring.autoconfigure.exclude=org.springframework.boot.autoconfigure.security.servlet.UserDetailsServiceAutoConfiguration"})
@Import({SecurityConfig.class, JwtFilter.class, BookingAccess.class, com.example.bookingservice.booking.service.CancellationPolicy.class})
class BookingAuthorizationTest {
    private static final UUID CUSTOMER = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final UUID OTHER = UUID.fromString("00000000-0000-0000-0000-000000000002");
    @Autowired MockMvc mvc;
    @MockBean BookingService service;
    @MockBean JwtService jwt;
    @MockBean BookingRepository bookings;
    @MockBean PaymentRepository payments;
    @MockBean PaymentService paymentService;
    @MockBean VnPayProperties vnPayProperties;
    @MockBean RestTemplate hotel;
    @MockBean com.example.bookingservice.payment.refund.RefundRequestService refunds;

    @BeforeEach
    void setUp() {
        when(jwt.validateToken(anyString())).thenReturn(true);
        when(jwt.extractUserId("customer")).thenReturn(CUSTOMER);
        when(jwt.extractUserId("other")).thenReturn(OTHER);
        when(jwt.extractUserId("owner")).thenReturn(OTHER);
        when(jwt.extractUserId("admin")).thenReturn(OTHER);
        when(jwt.extractRole(anyString())).thenReturn("CUSTOMER");
        when(jwt.extractRole("owner")).thenReturn("OWNER");
        when(jwt.extractRole("admin")).thenReturn("ADMIN");
        when(bookings.findById("booking-1")).thenReturn(Optional.of(Booking.builder()
                .id("booking-1").userId(CUSTOMER.toString()).hotelId("hotel-1").build()));
        when(service.getBooking("booking-1")).thenReturn(BookingResponse.builder().id("booking-1").build());
        when(service.getUserBookings(anyString())).thenReturn(List.of());
        when(service.getHotelBookings(anyString())).thenReturn(List.of());
        when(payments.findById("payment-1")).thenReturn(Optional.of(Payment.builder()
                .id("payment-1").bookingId("booking-1").build()));
        when(vnPayProperties.getHashSecret()).thenReturn("test-only-vnpay-signature-key");
        when(vnPayProperties.getTmnCode()).thenReturn("TEST_MERCHANT");
        when(payments.findByVnpayOrderId("payment-1")).thenReturn(Optional.of(Payment.builder()
                .id("payment-1").bookingId("booking-1").amount(new java.math.BigDecimal("1000000"))
                .method(com.example.bookingservice.payment.enums.PaymentMethod.VNPAY).build()));
        when(hotel.getForObject(anyString(), eq(Map.class), eq("hotel-1"))).thenReturn(Map.of("ownerId", OTHER.toString()));
    }

    @Test void anonymousCannotReadBooking() throws Exception {
        mvc.perform(get("/api/bookings/booking-1")).andExpect(status().isUnauthorized());
        verifyNoInteractions(service);
    }
    @Test void customerCanReadOwnBooking() throws Exception {
        mvc.perform(get("/api/bookings/booking-1").header("Authorization", "Bearer customer"))
                .andExpect(status().isOk());
    }
    @Test void customerCannotReadAnotherBookingEvenWithForgedHeaders() throws Exception {
        mvc.perform(get("/api/bookings/booking-1").header("Authorization", "Bearer other")
                        .header("X-User-Id", CUSTOMER).header("X-Is-Admin", "true"))
                .andExpect(status().isForbidden());
        verifyNoInteractions(service);
    }
    @Test void customerCannotListAnotherUsersBookings() throws Exception {
        mvc.perform(get("/api/bookings/user/" + OTHER).header("Authorization", "Bearer customer"))
                .andExpect(status().isForbidden());
        verifyNoInteractions(service);
    }
    @Test void customerCanListOwnBookings() throws Exception {
        mvc.perform(get("/api/bookings/user/" + CUSTOMER).header("Authorization", "Bearer customer"))
                .andExpect(status().isOk());
    }
    @Test void customerCannotCancelAnotherBooking() throws Exception {
        mvc.perform(delete("/api/bookings/booking-1").header("Authorization", "Bearer other"))
                .andExpect(status().isForbidden());
        verifyNoInteractions(service);
    }
    @Test void customerCanCancelOwnBooking() throws Exception {
        mvc.perform(delete("/api/bookings/booking-1").header("Authorization", "Bearer customer"))
                .andExpect(status().isNoContent());
        verify(service).cancelBooking("booking-1", CUSTOMER.toString());
    }
    @Test void customerCannotSetBookingStatus() throws Exception {
        mvc.perform(put("/api/bookings/booking-1/status").param("status", "CONFIRMED")
                        .header("Authorization", "Bearer customer"))
                .andExpect(status().isForbidden());
        verifyNoInteractions(service);
    }
    @Test void customerCannotListHotelBookings() throws Exception {
        mvc.perform(get("/api/bookings/hotel/hotel-1").header("Authorization", "Bearer customer"))
                .andExpect(status().isForbidden());
        verifyNoInteractions(service);
    }
    @Test void hotelOwnerCanListOwnHotelBookings() throws Exception {
        mvc.perform(get("/api/bookings/hotel/hotel-1").header("Authorization", "Bearer owner"))
                .andExpect(status().isOk());
    }
    @Test void unrelatedOwnerCannotListHotelBookings() throws Exception {
        when(hotel.getForObject(anyString(), eq(Map.class), eq("hotel-1"))).thenReturn(Map.of("ownerId", CUSTOMER.toString()));
        mvc.perform(get("/api/bookings/hotel/hotel-1").header("Authorization", "Bearer owner"))
                .andExpect(status().isForbidden());
        verifyNoInteractions(service);
    }
    @Test void administratorCanReadBooking() throws Exception {
        mvc.perform(get("/api/bookings/booking-1").header("Authorization", "Bearer admin"))
                .andExpect(status().isOk());
    }
    @Test void createCannotImpersonateAnotherUser() throws Exception {
        mvc.perform(post("/api/bookings").header("Authorization", "Bearer customer")
                        .contentType("application/json").content("{\"userId\":\"" + OTHER + "\"}"))
                .andExpect(status().isForbidden());
        verifyNoInteractions(service);
    }
    @Test void anonymousCannotReadGlobalCounts() throws Exception {
        mvc.perform(get("/api/bookings/count/total")).andExpect(status().isUnauthorized());
        verifyNoInteractions(service);
    }

    @Test void createUsesPrincipalWhenUserIdIsOmitted() throws Exception {
        when(service.createBooking(any())).thenAnswer(invocation -> BookingResponse.builder()
                .id("new-booking").userId(invocation.<com.example.bookingservice.booking.dto.CreateBookingRequest>getArgument(0).getUserId()).build());
        mvc.perform(post("/api/bookings").header("Authorization", "Bearer customer")
                        .contentType("application/json").content("{}"))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.userId").value(CUSTOMER.toString()));
    }

    @Test void anonymousCannotReadPayment() throws Exception {
        mvc.perform(get("/api/payments/payment-1")).andExpect(status().isUnauthorized());
        verifyNoInteractions(paymentService);
    }
    @Test void customerCannotReadAnotherPayment() throws Exception {
        mvc.perform(get("/api/payments/payment-1").header("Authorization", "Bearer other"))
                .andExpect(status().isForbidden());
        verifyNoInteractions(paymentService);
    }
    @Test void customerCanReadOwnPayment() throws Exception {
        mvc.perform(get("/api/payments/payment-1").header("Authorization", "Bearer customer"))
                .andExpect(status().isOk());
        verify(paymentService).getPayment("payment-1");
    }
    @Test void customerCannotPayForAnotherBooking() throws Exception {
        mvc.perform(post("/api/payments").header("Authorization", "Bearer other")
                        .contentType("application/json").content("{\"bookingId\":\"booking-1\",\"amount\":1,\"method\":\"HOTEL\"}"))
                .andExpect(status().isForbidden());
        verifyNoInteractions(paymentService);
    }
    @Test void customerCannotConfirmOnsitePayment() throws Exception {
        mvc.perform(post("/api/payments/booking-1/confirm").header("Authorization", "Bearer customer"))
                .andExpect(status().isForbidden());
        verifyNoInteractions(paymentService);
    }
    @Test void ownerCanConfirmPaymentForOwnHotel() throws Exception {
        mvc.perform(post("/api/payments/booking-1/confirm").header("Authorization", "Bearer owner"))
                .andExpect(status().isOk());
        verify(paymentService).confirmHotelPayment("booking-1");
    }
    @Test void invalidProviderSignatureIsRejectedWithoutUserAuthentication() throws Exception {
        mvc.perform(get("/api/payments/vnpay/return").param("vnp_SecureHash", "invalid"))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(paymentService);
    }

    @Test void customerCannotRefundAnotherPayment() throws Exception {
        mvc.perform(post("/api/payments/payment-1/refund").header("Authorization", "Bearer other"))
                .andExpect(status().isForbidden());
        verifyNoInteractions(paymentService);
    }

    @Test void missingBookingReturnsNotFound() throws Exception {
        mvc.perform(get("/api/bookings/missing").header("Authorization", "Bearer customer"))
                .andExpect(status().isNotFound());
        verifyNoInteractions(service);
    }

    @Test void ownershipLookupFailureDoesNotGrantOwnerAccess() throws Exception {
        when(hotel.getForObject(anyString(), eq(Map.class), eq("hotel-1")))
                .thenThrow(new org.springframework.web.client.ResourceAccessException("test outage"));
        mvc.perform(get("/api/bookings/hotel/hotel-1").header("Authorization", "Bearer owner"))
                .andExpect(status().isServiceUnavailable());
        verifyNoInteractions(service);
    }

    @Test void hotelOwnerCanUpdateStayStatus() throws Exception {
        when(service.updateBookingStatus(anyString(), any())).thenReturn(BookingResponse.builder().id("booking-1").build());
        mvc.perform(put("/api/bookings/booking-1/status").param("status", "CHECKED_IN")
                        .header("Authorization", "Bearer owner"))
                .andExpect(status().isOk());
        verify(service).updateBookingStatus("booking-1", com.example.bookingservice.booking.enums.BookingStatus.CHECKED_IN);
    }

    @Test void customerCannotExecuteRefundEvenForOwnPayment() throws Exception {
        mvc.perform(post("/api/payments/payment-1/refund").header("Authorization", "Bearer customer"))
                .andExpect(status().isForbidden());
        verifyNoInteractions(paymentService);
    }

    @Test void hotelOwnerCannotExecuteRefund() throws Exception {
        mvc.perform(post("/api/payments/payment-1/refund").header("Authorization", "Bearer owner"))
                .andExpect(status().isForbidden());
        verifyNoInteractions(paymentService);
    }

    @Test void administratorCanReachRefundExecution() throws Exception {
        mvc.perform(post("/api/payments/payment-1/refund").header("Authorization", "Bearer admin"))
                .andExpect(status().isOk());
        verify(paymentService).refundPayment("payment-1");
    }

    @Test void signedCallbackWithWrongAmountDoesNotProcessPayment() throws Exception {
        mvc.perform(signedReturn(Map.of("vnp_TxnRef", "payment-1", "vnp_ResponseCode", "00",
                        "vnp_TransactionNo", "123456789", "vnp_TmnCode", "TEST_MERCHANT",
                        "vnp_Amount", "1", "vnp_TransactionStatus", "00")))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(paymentService);
    }

    @Test void signedCallbackForAnotherMerchantDoesNotProcessPayment() throws Exception {
        mvc.perform(signedReturn(Map.of("vnp_TxnRef", "payment-1", "vnp_ResponseCode", "00",
                        "vnp_TransactionNo", "123456789", "vnp_TmnCode", "OTHER_MERCHANT",
                        "vnp_Amount", "100000000", "vnp_TransactionStatus", "00")))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(paymentService);
    }

    @Test void signedUnfinishedTransactionCannotClaimSuccessfulPayment() throws Exception {
        mvc.perform(signedReturn(Map.of("vnp_TxnRef", "payment-1", "vnp_ResponseCode", "00",
                        "vnp_TransactionNo", "123456789", "vnp_TmnCode", "TEST_MERCHANT",
                        "vnp_Amount", "100000000", "vnp_TransactionStatus", "05")))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(paymentService);
    }

    private org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder signedReturn(Map<String, String> params) {
        var builder = get("/api/payments/vnpay/return");
        params.forEach(builder::param);
        return builder.param("vnp_SecureHash", com.example.bookingservice.payment.vnpay.VnPayUtil.hmacSHA512(
                vnPayProperties.getHashSecret(), com.example.bookingservice.payment.vnpay.VnPayUtil.buildQuery(params)));
    }

    @Test void customerCanRequestOwnRefundWithoutExecutingIt() throws Exception {
        mvc.perform(post("/api/payments/payment-1/refund-request").header("Authorization", "Bearer customer")
                        .contentType("application/json").content("{\"reason\":\"Customer request\",\"amount\":1,\"requestedBy\":\"forged\"}"))
                .andExpect(status().isCreated());
        verify(refunds).request("payment-1", CUSTOMER.toString(), "Customer request");
        verifyNoInteractions(paymentService);
    }
    @Test void crossCustomerCannotRequestRefund() throws Exception {
        mvc.perform(post("/api/payments/payment-1/refund-request").header("Authorization", "Bearer other")
                        .contentType("application/json").content("{\"reason\":\"Request\"}"))
                .andExpect(status().isForbidden());
        verifyNoInteractions(refunds);
    }
    @Test void ownerCanRequestRefundButCannotApproveIt() throws Exception {
        mvc.perform(post("/api/payments/payment-1/refund-request").header("Authorization", "Bearer owner")
                        .contentType("application/json").content("{\"reason\":\"Hotel cannot supply room\"}"))
                .andExpect(status().isCreated());
        mvc.perform(post("/api/payments/payment-1/refund-request/approve").header("Authorization", "Bearer owner"))
                .andExpect(status().isForbidden());
        verify(refunds, never()).approve(anyString(), anyString());
    }
    @Test void administratorCanApproveRefundRequest() throws Exception {
        mvc.perform(post("/api/payments/payment-1/refund-request/approve").header("Authorization", "Bearer admin"))
                .andExpect(status().isOk());
        verify(refunds).approve("payment-1", OTHER.toString());
    }
    @Test void customerCannotUseReviewedCancellationEndpoint() throws Exception {
        mvc.perform(post("/api/bookings/booking-1/cancel-reviewed").header("Authorization", "Bearer customer")
                        .contentType("application/json").content("{\"reason\":\"Late cancellation\"}"))
                .andExpect(status().isForbidden());
        verifyNoInteractions(service);
    }
    @Test void policyCanBeReadBeforeBookingOrSigningIn() throws Exception {
        mvc.perform(get("/api/bookings/cancellation-policy")).andExpect(status().isOk())
                .andExpect(jsonPath("$.version").value("STANDARD_24H_V1"));
    }
    @Test void catalogOutageRemainsServiceUnavailable() throws Exception {
        when(service.createBooking(any())).thenThrow(new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.SERVICE_UNAVAILABLE));
        mvc.perform(post("/api/bookings").header("Authorization", "Bearer customer")
                        .contentType("application/json").content("{}"))
                .andExpect(status().isServiceUnavailable());
    }
    @Test void reviewedCancellationRequiresAReasonBeforeServiceMutation() throws Exception {
        mvc.perform(post("/api/bookings/booking-1/cancel-reviewed").header("Authorization", "Bearer admin")
                        .contentType("application/json").content("{\"reason\":\" \"}"))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(service);
    }

    @Test void validProviderSignatureStillWorksWithoutUserAuthentication() throws Exception {
        var params = Map.of("vnp_TxnRef", "payment-1", "vnp_ResponseCode", "00", "vnp_TransactionNo", "123456789",
                "vnp_TmnCode", "TEST_MERCHANT", "vnp_Amount", "100000000", "vnp_TransactionStatus", "00");
        mvc.perform(signedReturn(params))
                .andExpect(status().isOk());
        verify(paymentService).processVNPayCallback("payment-1", "00", "123456789");
    }
}

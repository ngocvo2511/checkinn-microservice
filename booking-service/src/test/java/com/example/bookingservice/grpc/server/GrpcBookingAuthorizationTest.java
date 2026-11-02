package com.example.bookingservice.grpc.server;

import com.example.bookingservice.booking.service.BookingService;
import com.example.bookingservice.grpc.GetBookingRequest;
import com.example.bookingservice.grpc.VNPayCallbackRequest;
import com.example.bookingservice.grpc.interceptor.UserContextInterceptor;
import com.example.bookingservice.payment.service.PaymentService;
import com.example.bookingservice.security.BookingAccess;
import io.grpc.Context;
import io.grpc.Status;
import io.grpc.stub.StreamObserver;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class GrpcBookingAuthorizationTest {
    private final BookingService bookings = mock(BookingService.class);
    private final PaymentService payments = mock(PaymentService.class);
    private final BookingAccess access = mock(BookingAccess.class);
    private final StreamObserver<com.example.bookingservice.grpc.BookingResponse> observer = mock(StreamObserver.class);

    @Test void directBookingRpcWithoutVerifiedContextDoesNotReadData() {
        new BookingGrpcServer(bookings, access).getBooking(GetBookingRequest.newBuilder().setId("booking-1").build(), observer);
        verify(observer).onError(argThat(error -> Status.fromThrowable(error).getCode() == Status.Code.UNAUTHENTICATED));
        verifyNoInteractions(bookings);
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.CsvSource({"hotel-service,HOTEL_SERVICE,true", "hotel-service,ADMIN,false",
            "other-service,HOTEL_SERVICE,false", "administrator,ADMIN,false", "guest,CUSTOMER,false", "owner,OWNER,false"})
    void eligibilityRpcRequiresExactHotelServiceIdentity(String subject, String role, boolean allowed) {
        var actor = new UsernamePasswordAuthenticationToken(subject, null, List.of(new SimpleGrantedAuthority("ROLE_" + role)));
        StreamObserver<com.example.bookingservice.grpc.ReviewEligibilityResponse> response = mock(StreamObserver.class);
        when(bookings.isReviewEligible("booking", "guest", "hotel")).thenReturn(true);
        Context.current().withValue(UserContextInterceptor.ACTOR, actor).run(() -> new BookingGrpcServer(bookings, access)
                .checkReviewEligibility(com.example.bookingservice.grpc.ReviewEligibilityRequest.newBuilder()
                        .setBookingId("booking").setGuestId("guest").setHotelId("hotel").build(), response));
        if (allowed) {
            verify(bookings).isReviewEligible("booking", "guest", "hotel");
            verify(response).onNext(argThat(com.example.bookingservice.grpc.ReviewEligibilityResponse::getEligible));
            verify(response).onCompleted();
        } else {
            verifyNoInteractions(bookings);
            verify(response).onError(argThat(error -> Status.fromThrowable(error).getCode() == Status.Code.PERMISSION_DENIED));
        }
    }

    @Test void anonymousCannotCheckEligibility() {
        StreamObserver<com.example.bookingservice.grpc.ReviewEligibilityResponse> response = mock(StreamObserver.class);
        new BookingGrpcServer(bookings, access).checkReviewEligibility(
                com.example.bookingservice.grpc.ReviewEligibilityRequest.getDefaultInstance(), response);
        verifyNoInteractions(bookings);
        verify(response).onError(argThat(error -> Status.fromThrowable(error).getCode() == Status.Code.UNAUTHENTICATED));
    }

    @Test void authenticatedCrossBookingRpcDoesNotReadData() {
        var actor = new UsernamePasswordAuthenticationToken("other", null, List.of(new SimpleGrantedAuthority("ROLE_CUSTOMER")));
        Context.current().withValue(UserContextInterceptor.ACTOR, actor).run(() ->
                new BookingGrpcServer(bookings, access).getBooking(GetBookingRequest.newBuilder().setId("booking-1").build(), observer));
        verify(access).canRead(actor, "booking-1");
        verify(observer).onError(argThat(error -> Status.fromThrowable(error).getCode() == Status.Code.PERMISSION_DENIED));
        verifyNoInteractions(bookings);
    }

    @Test void unsignedLegacyCallbackCannotChangePaymentEvenWithAnActor() {
        StreamObserver<com.example.bookingservice.grpc.PaymentResponse> response = mock(StreamObserver.class);
        new PaymentGrpcServer(payments, access).processVNPayCallback(VNPayCallbackRequest.newBuilder()
                .setOrderId("payment-1").setResponseCode("00").setTransactionId("forged").build(), response);
        verify(response).onError(argThat(error -> Status.fromThrowable(error).getCode() == Status.Code.PERMISSION_DENIED));
        verifyNoInteractions(payments);
        verify(response, never()).onNext(any());
    }
    @Test void ownerCannotExecuteRefundOverGrpc() {
        var actor = new UsernamePasswordAuthenticationToken("owner", null, List.of(new SimpleGrantedAuthority("ROLE_OWNER")));
        when(access.canReadPayment(actor, "payment-1")).thenReturn(true);
        StreamObserver<com.example.bookingservice.grpc.PaymentResponse> response = mock(StreamObserver.class);
        Context.current().withValue(UserContextInterceptor.ACTOR, actor).run(() -> new PaymentGrpcServer(payments, access)
                .refundPayment(com.example.bookingservice.grpc.RefundPaymentRequest.newBuilder().setId("payment-1").build(), response));
        verify(response).onError(argThat(error -> Status.fromThrowable(error).getCode() == Status.Code.PERMISSION_DENIED));
        verifyNoInteractions(payments);
    }
    @Test void administratorCannotBypassUnavailableRefundProviderOverGrpc() {
        var actor = new UsernamePasswordAuthenticationToken("administrator", null, List.of(new SimpleGrantedAuthority("ROLE_ADMIN")));
        when(access.isAdmin(actor)).thenReturn(true);
        doThrow(new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.SERVICE_UNAVAILABLE))
                .when(payments).refundPayment("payment-1");
        StreamObserver<com.example.bookingservice.grpc.PaymentResponse> response = mock(StreamObserver.class);
        Context.current().withValue(UserContextInterceptor.ACTOR, actor).run(() -> new PaymentGrpcServer(payments, access)
                .refundPayment(com.example.bookingservice.grpc.RefundPaymentRequest.newBuilder().setId("payment-1").build(), response));
        verify(response).onError(argThat(error -> Status.fromThrowable(error).getCode() == Status.Code.UNAVAILABLE));
    }
}

package com.example.travelsocialservice.client;

import com.example.bookingservice.grpc.BookingGrpcServiceGrpc;
import com.example.bookingservice.grpc.CompletedStayRequest;
import com.example.bookingservice.grpc.CompletedStayResponse;
import com.example.travelsocialservice.client.CompletedStays.CompletedStay;
import com.example.travelsocialservice.client.CompletedStays.StayLookupUnavailableException;
import com.example.travelsocialservice.client.HotelClient.HotelRef;
import com.example.travelsocialservice.config.SocialProperties;
import com.example.travelsocialservice.security.ServiceTokenProvider;
import com.example.travelsocialservice.web.ApiException;
import io.grpc.Status;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.ExpectedCount;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.time.Duration;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class StayClientsTest {

    private static final SocialProperties PROPERTIES = new SocialProperties("http://place.invalid", "http://user.invalid",
            "http://hotel.invalid",
            new SocialProperties.Client(Duration.ofSeconds(2), Duration.ofSeconds(3), Duration.ofMinutes(5)),
            null, null, null, null);

    @Test
    void publishedHotelIsReturnedAndOmittedHotelIsEmpty() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        UUID published = UUID.randomUUID();
        UUID draft = UUID.randomUUID();
        server.expect(requestTo("http://hotel.invalid/hotels/batch?ids=" + published))
                .andRespond(withSuccess("[{\"id\":\"" + published + "\",\"name\":\"Pine Hill\",\"cityName\":\"Đà Lạt\","
                        + "\"provinceName\":\"Lâm Đồng\",\"starRating\":4,\"coverImageUrl\":null,\"extra\":1}]",
                        MediaType.APPLICATION_JSON));
        server.expect(requestTo("http://hotel.invalid/hotels/batch?ids=" + draft))
                .andRespond(withSuccess("[]", MediaType.APPLICATION_JSON));
        HotelClient client = new HotelClient(builder, PROPERTIES);

        assertThat(client.require(published))
                .contains(new HotelRef(published, "Pine Hill", "Đà Lạt", "Lâm Đồng", (short) 4, null));
        assertThat(client.require(draft)).isEmpty();
        server.verify();
    }

    @Test
    void publishingFailsButRenderingContinuesWhenTheHotelCatalogIsDown() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        server.expect(ExpectedCount.times(2), requestTo(org.hamcrest.Matchers.startsWith("http://hotel.invalid/hotels/batch")))
                .andRespond(withServerError());
        HotelClient client = new HotelClient(builder, PROPERTIES);

        assertThatThrownBy(() -> client.require(UUID.randomUUID())).isInstanceOf(ApiException.class)
                .satisfies(ex -> {
                    assertThat(((ApiException) ex).getStatus()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
                    assertThat(((ApiException) ex).getCode()).isEqualTo("HOTEL_SERVICE_UNAVAILABLE");
                });
        assertThat(client.findAll(List.of(UUID.randomUUID()))).isEmpty();
        assertThat(client.findAll(List.of())).isEmpty();
    }

    private static BookingGrpcServiceGrpc.BookingGrpcServiceBlockingStub stub() {
        BookingGrpcServiceGrpc.BookingGrpcServiceBlockingStub stub = mock(BookingGrpcServiceGrpc.BookingGrpcServiceBlockingStub.class);
        when(stub.withInterceptors(any())).thenReturn(stub);
        when(stub.withDeadlineAfter(anyLong(), any())).thenReturn(stub);
        return stub;
    }

    private static GrpcCompletedStays completedStays(BookingGrpcServiceGrpc.BookingGrpcServiceBlockingStub stub) {
        ServiceTokenProvider tokens = mock(ServiceTokenProvider.class);
        when(tokens.authorization()).thenReturn("Bearer service-token");
        return new GrpcCompletedStays(tokens, PROPERTIES, stub);
    }

    @Test
    void completedStayIsReadWithTheServiceTokenAndADeadline() {
        var stub = stub();
        UUID booking = UUID.randomUUID();
        UUID user = UUID.randomUUID();
        UUID hotel = UUID.randomUUID();
        when(stub.getCompletedStay(CompletedStayRequest.newBuilder().setBookingId(booking.toString()).build()))
                .thenReturn(CompletedStayResponse.newBuilder().setUserId(user.toString()).setHotelId(hotel.toString())
                        .setCheckInDate("2026-11-01").setCheckOutDate("2026-11-03").build());

        assertThat(completedStays(stub).find(booking)).contains(
                new CompletedStay(user, hotel, LocalDate.of(2026, 11, 1), LocalDate.of(2026, 11, 3)));
        verify(stub).withDeadlineAfter(3000L, TimeUnit.MILLISECONDS);
    }

    @Test
    void notFoundMeansNoCompletedStay() {
        var stub = stub();
        when(stub.getCompletedStay(any())).thenThrow(Status.NOT_FOUND.asRuntimeException());

        assertThat(completedStays(stub).find(UUID.randomUUID())).isEmpty();
    }

    @Test
    void outagesAndDenialsAreNeverMistakenForNoStay() {
        for (Status status : new Status[]{Status.UNAVAILABLE, Status.DEADLINE_EXCEEDED, Status.PERMISSION_DENIED,
                Status.UNAUTHENTICATED, Status.INTERNAL}) {
            var stub = stub();
            when(stub.getCompletedStay(any())).thenThrow(status.asRuntimeException());
            assertThatThrownBy(() -> completedStays(stub).find(UUID.randomUUID()))
                    .as(status.getCode().name()).isInstanceOf(StayLookupUnavailableException.class);
        }
    }

    @Test
    void aMalformedAnswerIsAnOutage() {
        var stub = stub();
        when(stub.getCompletedStay(any())).thenReturn(CompletedStayResponse.newBuilder()
                .setUserId("not-a-uuid").setHotelId(UUID.randomUUID().toString())
                .setCheckInDate("2026-11-01").setCheckOutDate("2026-11-03").build());

        assertThatThrownBy(() -> completedStays(stub).find(UUID.randomUUID()))
                .isInstanceOf(StayLookupUnavailableException.class);
        verify(stub, never()).withDeadlineAfter(eq(0L), any());
    }
}

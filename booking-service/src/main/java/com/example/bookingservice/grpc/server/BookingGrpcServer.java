package com.example.bookingservice.grpc.server;

import com.example.bookingservice.booking.dto.BookingItemResponse;
import com.example.bookingservice.booking.dto.BookingResponse;
import com.example.bookingservice.booking.dto.CreateBookingItemRequest;
import com.example.bookingservice.booking.dto.CreateBookingRequest;
import com.example.bookingservice.booking.service.BookingService;
import com.example.bookingservice.grpc.BookingGrpcServiceGrpc;
import io.grpc.stub.StreamObserver;
import net.devh.boot.grpc.server.service.GrpcService;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.stream.Collectors;

@GrpcService
public class BookingGrpcServer extends BookingGrpcServiceGrpc.BookingGrpcServiceImplBase {

    private final BookingService bookingService;

    public BookingGrpcServer(BookingService bookingService) {
        this.bookingService = bookingService;
    }

    @Override
    public void createBooking(com.example.bookingservice.grpc.CreateBookingRequest request, StreamObserver<com.example.bookingservice.grpc.BookingResponse> responseObserver) {
        try {
            CreateBookingRequest create = CreateBookingRequest.builder()
                    .userId(request.getUserId())
                    .hotelId(request.getHotelId())
                    .hotelName(request.getHotelName())
                    .checkInDate(LocalDate.parse(request.getCheckInDate()))
                    .checkOutDate(LocalDate.parse(request.getCheckOutDate()))
                    .adults(request.getAdults())
                    .children(request.getChildren())
                    .contactName(request.getContactName())
                    .contactEmail(request.getContactEmail())
                    .contactPhone(request.getContactPhone())
                    .specialRequests(request.getSpecialRequests())
                    .voucherCode(request.getVoucherCode())
                    .items(request.getItemsList().stream().map(item -> CreateBookingItemRequest.builder()
                            .roomTypeId(item.getRoomTypeId())
                            .roomTypeName(item.getRoomTypeName())
                            .ratePlanId(item.getRatePlanId())
                            .checkInDate(LocalDate.parse(item.getCheckInDate()))
                            .checkOutDate(LocalDate.parse(item.getCheckOutDate()))
                            .quantity(item.getQuantity())
                            .unitPrice(BigDecimal.valueOf(item.getUnitPrice()))
                            .nights(item.getNights())
                            .guestName(item.getGuestName())
                            .cancellationPolicy(item.getCancellationPolicy())
                            .build()).collect(Collectors.toList()))
                    .build();

            BookingResponse booking = bookingService.createBooking(create);
            responseObserver.onNext(toProto(booking));
            responseObserver.onCompleted();
        } catch (Exception ex) {
            responseObserver.onError(ex);
        }
    }

    @Override
    public void getBooking(com.example.bookingservice.grpc.GetBookingRequest request, StreamObserver<com.example.bookingservice.grpc.BookingResponse> responseObserver) {
        try {
            BookingResponse booking = bookingService.getBooking(request.getId());
            responseObserver.onNext(toProto(booking));
            responseObserver.onCompleted();
        } catch (Exception ex) {
            responseObserver.onError(ex);
        }
    }

    private com.example.bookingservice.grpc.BookingResponse toProto(BookingResponse booking) {
        com.example.bookingservice.grpc.BookingResponse.Builder builder = com.example.bookingservice.grpc.BookingResponse.newBuilder()
                .setId(booking.getId())
                .setUserId(booking.getUserId())
                .setHotelId(booking.getHotelId())
                .setHotelName(booking.getHotelName())
                .setCheckInDate(String.valueOf(booking.getCheckInDate()))
                .setCheckOutDate(String.valueOf(booking.getCheckOutDate()))
                .setAdults(booking.getAdults())
                .setChildren(booking.getChildren())
                .setStatus(booking.getStatus().name())
                .setTotalAmount(booking.getTotalAmount().doubleValue())
                .setPaidAmount(booking.getPaidAmount().doubleValue())
                .setVoucherCode(booking.getVoucherCode() == null ? "" : booking.getVoucherCode())
                .setVoucherDiscount(booking.getVoucherDiscount() == null ? 0D : booking.getVoucherDiscount().doubleValue())
                .setContactName(booking.getContactName())
                .setContactEmail(booking.getContactEmail())
                .setContactPhone(booking.getContactPhone())
                .setSpecialRequests(booking.getSpecialRequests() == null ? "" : booking.getSpecialRequests())
                .setCreatedAt(String.valueOf(booking.getCreatedAt()))
                .setUpdatedAt(String.valueOf(booking.getUpdatedAt()));

        for (BookingItemResponse item : booking.getItems()) {
            builder.addItems(com.example.bookingservice.grpc.BookingItemResponse.newBuilder()
                    .setId(item.getId())
                    .setRoomTypeId(item.getRoomTypeId())
                    .setRoomTypeName(item.getRoomTypeName())
                    .setRatePlanId(item.getRatePlanId())
                    .setCheckInDate(String.valueOf(item.getCheckInDate()))
                    .setCheckOutDate(String.valueOf(item.getCheckOutDate()))
                    .setQuantity(item.getQuantity())
                    .setUnitPrice(item.getUnitPrice().doubleValue())
                    .setNights(item.getNights())
                    .setSubtotal(item.getSubtotal().doubleValue())
                    .setTaxFee(item.getTaxFee() == null ? 0D : item.getTaxFee().doubleValue())
                    .setCancellationPolicy(item.getCancellationPolicy() == null ? "" : item.getCancellationPolicy())
                    .setGuestName(item.getGuestName() == null ? "" : item.getGuestName())
                    .build());
        }
        return builder.build();
    }
}

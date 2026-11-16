package com.example.bookingservice.messaging;

import static com.example.bookingservice.contract.UuidContract.*;
import com.example.bookingservice.booking.entity.Booking;
import com.example.bookingservice.messaging.event.BookingStatusEvent;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;

public final class BookingStatusSnapshots {
    private BookingStatusSnapshots() {}
    public static BookingStatusEvent from(Booking booking) {
        int rooms=booking.getItems()==null?0:booking.getItems().stream()
                .mapToInt(item->item.getQuantity()==null?0:item.getQuantity()).sum();
        return BookingStatusEvent.builder().bookingId(text(booking.getId())).hotelId(text(booking.getHotelId()))
                .roomTypeId(text(booking.getItems()==null || booking.getItems().isEmpty()?null:booking.getItems().getFirst().getRoomTypeId()))
                .rooms(rooms).checkInDate(booking.getCheckInDate()).checkOutDate(booking.getCheckOutDate())
                .nights(booking.getCheckInDate()==null || booking.getCheckOutDate()==null?0:
                        Math.toIntExact(ChronoUnit.DAYS.between(booking.getCheckInDate(),booking.getCheckOutDate())))
                .bookingStatus(booking.getStatus().name()).eventAt(LocalDateTime.now()).build();
    }
}

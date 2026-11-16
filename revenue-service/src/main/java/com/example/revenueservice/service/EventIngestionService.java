package com.example.revenueservice.service;

import static com.example.revenueservice.contract.UuidContract.*;
import com.example.revenueservice.entity.*;
import com.example.revenueservice.messaging.event.*;
import com.example.revenueservice.repository.*;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.*;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.security.MessageDigest;
import java.nio.charset.StandardCharsets;

/** Serialized, monotonic legacy-event projection. Ambiguous inputs are durably quarantined. */
@Service
public class EventIngestionService {
    private final PaymentRecordRepository payments;
    private final BookingStatusRecordRepository bookings;
    private final JdbcTemplate jdbc;
    private final ObjectMapper json;
    private final CashTransactionWriter cash;
    public EventIngestionService(PaymentRecordRepository payments, BookingStatusRecordRepository bookings,
                                 JdbcTemplate jdbc, ObjectMapper json,CashTransactionWriter cash) {
        this.payments=payments;this.bookings=bookings;this.jdbc=jdbc;this.json=json;this.cash=cash;
    }
    private boolean valid(String booking,String hotel,LocalDate from,LocalDate to,int nights,int rooms,LocalDateTime at) {
        return com.example.revenueservice.contract.UuidContract.valid(booking) && com.example.revenueservice.contract.UuidContract.valid(hotel) && from!=null && to!=null
                && to.isAfter(from) && ChronoUnit.DAYS.between(from,to)==nights && rooms>0 && at!=null;
    }
    private void lock(String kind,String id) {
        jdbc.query("SELECT pg_advisory_xact_lock(hashtextextended(?,0))",rs->{},"revenue:"+kind+":"+required(id));
    }
    private LocalDateTime time(LocalDateTime at) { return at.truncatedTo(ChronoUnit.MICROS); }
    private void receipt(String kind,String booking,LocalDateTime at,Object event,String disposition,String reason) {
        try {
            String payload=json.writeValueAsString(event);
            String hash=HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(payload.getBytes(StandardCharsets.UTF_8)));
            jdbc.update("INSERT INTO revenue_event_receipts(id,kind,booking_id,event_hash,event_at,disposition,reason,payload) VALUES (?,?,?,?,?,?,?,CAST(? AS jsonb)) ON CONFLICT(kind,event_hash) DO NOTHING",
                    UUID.randomUUID(),kind,booking,hash,at,disposition,reason,payload);
            org.slf4j.LoggerFactory.getLogger(EventIngestionService.class).info("[REVENUE_EVENT_RECEIPT_STAGED] {} {}",disposition,reason);
        } catch(JsonProcessingException | java.security.NoSuchAlgorithmException failure) {
            throw new IllegalStateException("Revenue event encoding failed");
        }
    }
    private List<?> fields(BookingStatusRecord r) {
        return Arrays.asList(r.getBookingId(),r.getHotelId(),r.getRoomTypeId(),r.getCheckInDate(),r.getCheckOutDate(),r.getNights(),r.getRooms(),r.getBookingStatus());
    }
    private List<?> fields(PaymentRecord r) {
        return Arrays.asList(r.getBookingId(),r.getCustomerId(),r.getHotelId(),r.getRoomTypeId(),r.getCheckInDate(),r.getCheckOutDate(),r.getNights(),r.getRooms(),
                r.getAmount()==null?null:r.getAmount().stripTrailingZeros(),r.getPaymentStatus(),r.getPaymentMethod(),r.getPaidAt());
    }
    private boolean metadata(Long version,UUID id) {
        return version==null?id==null:version>0 && id!=null;
    }
    private int order(Long incoming,Long current,LocalDateTime at,LocalDateTime previous) {
        if(incoming!=null)return current==null?1:incoming.compareTo(current);
        return at.compareTo(previous);
    }
    @Transactional
    public void saveBookingStatusEvent(BookingStatusEvent e) {
        if(e==null || !metadata(e.eventVersion(),e.eventId()) || !valid(e.bookingId(),e.hotelId(),e.checkInDate(),e.checkOutDate(),e.nights(),e.rooms(),e.eventAt())
                || (e.roomTypeId()!=null && !com.example.revenueservice.contract.UuidContract.valid(e.roomTypeId()))
                || !Set.of("PENDING","PENDING_PAYMENT","CONFIRMED","CHECKED_IN","CHECKED_OUT","CANCELLED","NO_SHOW").contains(e.bookingStatus()==null?"":e.bookingStatus())) {
            receipt("BOOKING",e==null?null:e.bookingId(),e==null?null:e.eventAt(),e,"REVIEW_REQUIRED","INVALID_SOURCE_EVENT");return;
        }
        lock("BOOKING",e.bookingId());
        var current=bookings.findByBookingId(required(e.bookingId())).orElse(null);
        var next=new BookingStatusRecord();
        next.setBookingId(optional(e.bookingId()));next.setHotelId(optional(e.hotelId()));next.setRoomTypeId(optional(e.roomTypeId()));
        next.setCheckInDate(e.checkInDate());next.setCheckOutDate(e.checkOutDate());next.setNights(e.nights());next.setRooms(e.rooms());next.setBookingStatus(e.bookingStatus());
        LocalDateTime at=time(e.eventAt());
        if(current!=null) {
            if(current.getEventAt()==null || !Objects.equals(current.getHotelId(),optional(e.hotelId()))) {
                receipt("BOOKING",e.bookingId(),at,e,"REVIEW_REQUIRED","SOURCE_IDENTITY_OR_TIME_CONFLICT");return;
            }
            if(current.getEventVersion()!=null && e.eventVersion()==null) {
                receipt("BOOKING",e.bookingId(),at,e,"REVIEW_REQUIRED","LEGACY_AFTER_VERSIONED_SOURCE");return;
            }
            int order=order(e.eventVersion(),current.getEventVersion(),at,current.getEventAt());
            if(order<0) { receipt("BOOKING",e.bookingId(),at,e,"IGNORED","STALE_SOURCE_EVENT");return; }
            if(order==0) {
                boolean same=fields(current).equals(fields(next)) && at.equals(current.getEventAt()) && Objects.equals(e.eventId(),current.getLastEventId());
                receipt("BOOKING",e.bookingId(),at,e,same?"IGNORED":"REVIEW_REQUIRED",same?"DUPLICATE_SOURCE_EVENT":"CONFLICTING_SOURCE_TIME");return;
            }
        }
        var target=current==null?next:current;
        target.setRoomTypeId(optional(e.roomTypeId()));target.setCheckInDate(e.checkInDate());target.setCheckOutDate(e.checkOutDate());
        target.setNights(e.nights());target.setRooms(e.rooms());target.setBookingStatus(e.bookingStatus());target.setEventAt(at);
        target.setEventVersion(e.eventVersion());target.setLastEventId(e.eventId());
        if(target.getCreatedAt()==null)target.setCreatedAt(LocalDateTime.now());
        bookings.save(target);
        receipt("BOOKING",e.bookingId(),at,e,"APPLIED","VALID_NEWER_SOURCE_EVENT");
    }
    @Transactional
    public void savePaymentEvent(PaymentEvent e) {
        if(e==null || !metadata(e.eventVersion(),e.eventId()) || !valid(e.bookingId(),e.hotelId(),e.checkInDate(),e.checkOutDate(),e.nights(),e.rooms(),e.eventAt())
                || !com.example.revenueservice.contract.UuidContract.valid(e.customerId()) || (e.roomTypeId()!=null && !com.example.revenueservice.contract.UuidContract.valid(e.roomTypeId())) || e.amount()==null || e.amount().signum()<0 || e.paymentMethod()==null || e.paymentMethod().isBlank()
                || !Set.of("PENDING","ONSITE_PENDING","COMPLETED","FAILED","REFUNDED","CANCELLED").contains(e.paymentStatus()==null?"":e.paymentStatus())) {
            receipt("PAYMENT",e==null?null:e.bookingId(),e==null?null:e.eventAt(),e,"REVIEW_REQUIRED","INVALID_SOURCE_EVENT");return;
        }
        lock("PAYMENT",e.bookingId());
        var current=payments.findByBookingId(required(e.bookingId())).orElse(null);
        if(current!=null && (!Objects.equals(current.getHotelId(),optional(e.hotelId())) || !Objects.equals(current.getCustomerId(),optional(e.customerId())))) {
            receipt("PAYMENT",e.bookingId(),e.eventAt(),e,"REVIEW_REQUIRED","SOURCE_IDENTITY_OR_TIME_CONFLICT");return;
        }
        cash.preserveLegacyCoverage(e.bookingId());
        var cashOutcome=cash.record(e);
        if(cashOutcome==CashTransactionWriter.Outcome.MISSING_TRANSACTION_FACTS || cashOutcome==CashTransactionWriter.Outcome.TRANSACTION_CONFLICT) {
            receipt("PAYMENT",e.bookingId(),e.eventAt(),e,"REVIEW_REQUIRED",cashOutcome.name());return;
        }
        var next=new PaymentRecord();
        next.setBookingId(optional(e.bookingId()));next.setCustomerId(optional(e.customerId()));next.setHotelId(optional(e.hotelId()));next.setRoomTypeId(optional(e.roomTypeId()));
        next.setCheckInDate(e.checkInDate());next.setCheckOutDate(e.checkOutDate());next.setNights(e.nights());next.setRooms(e.rooms());
        next.setAmount(e.amount());next.setPaymentStatus(e.paymentStatus());next.setPaymentMethod(e.paymentMethod());
        next.setPaidAt(e.paidAt()==null?null:time(e.paidAt()));
        LocalDateTime at=time(e.eventAt());
        if(current!=null) {
            if(current.getEventAt()==null || !Objects.equals(current.getHotelId(),optional(e.hotelId())) || !Objects.equals(current.getCustomerId(),optional(e.customerId()))) {
                receipt("PAYMENT",e.bookingId(),at,e,"REVIEW_REQUIRED","SOURCE_IDENTITY_OR_TIME_CONFLICT");return;
            }
            if(current.getEventVersion()!=null && e.eventVersion()==null) {
                receipt("PAYMENT",e.bookingId(),at,e,"REVIEW_REQUIRED","LEGACY_AFTER_VERSIONED_SOURCE");return;
            }
            int order=order(e.eventVersion(),current.getEventVersion(),at,current.getEventAt());
            if(order<0) { receipt("PAYMENT",e.bookingId(),at,e,"IGNORED","STALE_SOURCE_EVENT");return; }
            if(order==0) {
                boolean same=fields(current).equals(fields(next)) && at.equals(current.getEventAt()) && Objects.equals(e.eventId(),current.getLastEventId());
                receipt("PAYMENT",e.bookingId(),at,e,same?"IGNORED":"REVIEW_REQUIRED",same?"DUPLICATE_SOURCE_EVENT":"CONFLICTING_SOURCE_TIME");return;
            }
        }
        var target=current==null?next:current;
        target.setRoomTypeId(optional(e.roomTypeId()));target.setCheckInDate(e.checkInDate());target.setCheckOutDate(e.checkOutDate());
        target.setNights(e.nights());target.setRooms(e.rooms());target.setAmount(e.amount());target.setPaymentStatus(e.paymentStatus());target.setPaymentMethod(e.paymentMethod());target.setPaidAt(next.getPaidAt());target.setEventAt(at);
        target.setEventVersion(e.eventVersion());target.setLastEventId(e.eventId());
        if(target.getCreatedAt()==null)target.setCreatedAt(LocalDateTime.now());
        payments.save(target);
        receipt("PAYMENT",e.bookingId(),at,e,"APPLIED","VALID_NEWER_SOURCE_EVENT");
    }
}

package com.example.revenueservice.service;

import com.example.revenueservice.messaging.event.PaymentEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import java.util.*;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;

/** Called while the ingestion transaction holds its booking lock. Full-payment V1 only. */
@Component
@RequiredArgsConstructor
public class CashTransactionWriter {
    private final JdbcTemplate jdbc;
    public enum Outcome { RECORDED, LEGACY_PROJECTION_ONLY, MISSING_TRANSACTION_FACTS, TRANSACTION_CONFLICT }
    public void preserveLegacyCoverage(String bookingId) {
        jdbc.update("INSERT INTO revenue_cashflow_gaps(booking_id,hotel_id,source_record_id,reason) SELECT booking_id,hotel_id,id,'LEGACY_HISTORY_NOT_VERIFIED' FROM payment_records WHERE booking_id=? AND event_version IS NULL ON CONFLICT(booking_id) DO NOTHING",bookingId);
    }
    public Outcome record(PaymentEvent e) {
        if(e.eventVersion()==null) {
            jdbc.update("INSERT INTO revenue_cashflow_gaps(booking_id,hotel_id,reason) VALUES (?,?,'LEGACY_HISTORY_NOT_VERIFIED') ON CONFLICT(booking_id) DO NOTHING",e.bookingId(),e.hotelId());
            return Outcome.LEGACY_PROJECTION_ONLY;
        }
        if(e.paymentId()==null || e.paymentId().isBlank() || e.transactionId()==null || e.transactionId().isBlank()
                || e.transactionId().length()>255 || e.transactionAt()==null || e.amount().signum()<=0
                || !Set.of("COMPLETED","REFUNDED").contains(e.paymentStatus()))return Outcome.MISSING_TRANSACTION_FACTS;
        String kind=e.paymentStatus().equals("COMPLETED")?"CREDIT":"DEBIT";
        // Payment identity spans bookings: their separate ingestion locks cannot serialize
        // conflicting CREDIT/DEBIT arrivals that claim the same provider payment.
        jdbc.query("SELECT pg_advisory_xact_lock(hashtextextended(?,0))",rs->{},"revenue:cash-payment:"+e.paymentId());
        var opposite=jdbc.queryForList("SELECT * FROM revenue_cash_transactions WHERE payment_id=? AND kind<>?",e.paymentId(),kind);
        if(!opposite.isEmpty() && !samePayment(opposite.getFirst(),e))
            return Outcome.TRANSACTION_CONFLICT;
        jdbc.update("""
            INSERT INTO revenue_cash_transactions(id,source_event_id,source_version,payment_id,kind,transaction_id,occurred_at,
                booking_id,customer_id,hotel_id,room_type_id,check_in_date,check_out_date,nights,rooms,amount,payment_method)
            VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?) ON CONFLICT DO NOTHING
            """,UUID.randomUUID(),e.eventId(),e.eventVersion(),e.paymentId(),kind,e.transactionId(),OffsetDateTime.ofInstant(e.transactionAt().truncatedTo(java.time.temporal.ChronoUnit.MICROS),ZoneOffset.UTC),
                e.bookingId(),e.customerId(),e.hotelId(),e.roomTypeId(),e.checkInDate(),e.checkOutDate(),e.nights(),e.rooms(),e.amount(),e.paymentMethod());
        // Re-emission with another outbox ID is still the same financial transaction.
        var rows=jdbc.queryForList("SELECT * FROM revenue_cash_transactions WHERE payment_id=? AND kind=?",e.paymentId(),kind);
        if(rows.size()!=1)return Outcome.TRANSACTION_CONFLICT;
        var row=rows.getFirst();
        boolean same=samePayment(row,e) && Objects.equals(row.get("transaction_id"),e.transactionId())
                && ((java.sql.Timestamp)row.get("occurred_at")).toInstant().equals(e.transactionAt().truncatedTo(java.time.temporal.ChronoUnit.MICROS));
        return same?Outcome.RECORDED:Outcome.TRANSACTION_CONFLICT;
    }
    private boolean samePayment(Map<String,Object> row,PaymentEvent e) {
        return Objects.equals(row.get("booking_id"),e.bookingId()) && Objects.equals(row.get("customer_id"),e.customerId())
                && Objects.equals(row.get("hotel_id"),e.hotelId())
                && Objects.equals(row.get("payment_method"),e.paymentMethod()) && Objects.equals(row.get("room_type_id"),e.roomTypeId())
                && Objects.equals(((java.sql.Date)row.get("check_in_date")).toLocalDate(),e.checkInDate())
                && Objects.equals(((java.sql.Date)row.get("check_out_date")).toLocalDate(),e.checkOutDate())
                && ((Number)row.get("rooms")).intValue()==e.rooms() && ((Number)row.get("nights")).intValue()==e.nights()
                && ((java.math.BigDecimal)row.get("amount")).compareTo(e.amount())==0;
    }
}

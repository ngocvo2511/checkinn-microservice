package com.example.revenueservice.service;

import com.example.revenueservice.client.HotelGrpcClient;
import com.example.revenueservice.entity.PaymentRecord;
import com.example.revenueservice.exception.ReportReconciliationRequiredException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import java.time.*;
import java.util.*;

/** Reporting adapter keeps existing response shapes while reading immutable cash movements. */
@Service
public class RevenueCashFlows {
    private final JdbcTemplate jdbc;
    private final HotelGrpcClient hotels;
    private final ZoneId zone;
    public RevenueCashFlows(JdbcTemplate jdbc,HotelGrpcClient hotels,@Value("${reporting.zone:Asia/Ho_Chi_Minh}") String zone) {
        this.jdbc=jdbc;this.hotels=hotels;this.zone=ZoneId.of(zone);
    }
    public List<PaymentRecord> read(String hotelId,LocalDate from,LocalDate to) {
        return readScope(hotelId==null?null:Set.of(hotelId),from,to);
    }
    public List<PaymentRecord> readOwner(String ownerId,LocalDate from,LocalDate to) {
        var ids=jdbc.queryForList("SELECT hotel_id FROM payment_records UNION SELECT hotel_id FROM revenue_cash_transactions UNION SELECT hotel_id FROM revenue_cashflow_gaps UNION SELECT payload->>'hotelId' FROM revenue_event_receipts WHERE kind='PAYMENT' AND disposition='REVIEW_REQUIRED'",String.class);
        Set<String> owned=new HashSet<>();
        for(String id:ids)if(id!=null && ownerId.equals(hotels.getHotelInfo(id).ownerId()))owned.add(id);
        return readScope(owned,from,to);
    }
    private List<PaymentRecord> readScope(Set<String> ids,LocalDate from,LocalDate to) {
        if(ids!=null && ids.isEmpty())return List.of();
        String clause=ids==null?"":" AND hotel_id IN ("+String.join(",",Collections.nCopies(ids.size(),"?"))+")";
        Object[] scope=ids==null?new Object[0]:ids.toArray();
        if(Boolean.TRUE.equals(jdbc.queryForObject("SELECT EXISTS(SELECT 1 FROM revenue_cashflow_gaps WHERE resolved_at IS NULL"+clause+")",Boolean.class,scope)))
            throw new ReportReconciliationRequiredException();
        if(Boolean.TRUE.equals(jdbc.queryForObject("SELECT EXISTS(SELECT 1 FROM payment_records WHERE event_version IS NULL"+clause+")",Boolean.class,scope)))
            throw new ReportReconciliationRequiredException();
        String reviews=ids==null?"":" AND payload->>'hotelId' IN ("+String.join(",",Collections.nCopies(ids.size(),"?"))+")";
        if(Boolean.TRUE.equals(jdbc.queryForObject("SELECT EXISTS(SELECT 1 FROM revenue_event_receipts WHERE kind='PAYMENT' AND disposition='REVIEW_REQUIRED'"+reviews+")",Boolean.class,scope)))
            throw new ReportReconciliationRequiredException();
        var args=new ArrayList<Object>();
        args.add(OffsetDateTime.ofInstant(from.atStartOfDay(zone).toInstant(),ZoneOffset.UTC));
        args.add(OffsetDateTime.ofInstant(to.plusDays(1).atStartOfDay(zone).toInstant(),ZoneOffset.UTC));
        if(ids!=null)args.addAll(ids);
        return jdbc.query("SELECT * FROM revenue_cash_transactions WHERE occurred_at>=? AND occurred_at<?"+clause+" ORDER BY occurred_at,id",(r,n)->{
            var row=new PaymentRecord();row.setBookingId(r.getString("booking_id"));row.setCustomerId(r.getString("customer_id"));row.setHotelId(r.getString("hotel_id"));row.setRoomTypeId(r.getString("room_type_id"));
            row.setCheckInDate(r.getObject("check_in_date",LocalDate.class));row.setCheckOutDate(r.getObject("check_out_date",LocalDate.class));row.setNights(r.getInt("nights"));row.setRooms(r.getInt("rooms"));row.setAmount(r.getBigDecimal("amount"));
            row.setPaymentStatus(r.getString("kind").equals("CREDIT")?"COMPLETED":"REFUNDED");row.setPaymentMethod(r.getString("payment_method"));
            var at=r.getTimestamp("occurred_at").toInstant().atZone(zone).toLocalDateTime();row.setPaidAt(at);row.setEventAt(at);return row;
        },args.toArray());
    }
}

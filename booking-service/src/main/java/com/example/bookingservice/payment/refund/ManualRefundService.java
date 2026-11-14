package com.example.bookingservice.payment.refund;

import static com.example.bookingservice.contract.UuidContract.*;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;
import java.util.*;

/** Internal manual workflow only. No accepted settlement verification source is configured. */
@Service
@RequiredArgsConstructor
public class ManualRefundService {
    private final RefundRequestService requests;
    private final JdbcTemplate jdbc;

    @Transactional
    public Map<String,Object> start(UUID paymentId, String actor) {
        requests.validateExecution(paymentId); // booking -> payment, retained until commit
        var request = requests.get(paymentId);
        int inserted = jdbc.update("""
            INSERT INTO manual_refund_executions(id,request_id,payment_id,booking_id,amount,currency,provider,state)
            SELECT ?,?,?,?,r.amount,'VND',p.method,'PENDING_MANUAL_REFUND'
            FROM refund_requests r JOIN payments p ON p.id=r.payment_id WHERE r.id=?
            ON CONFLICT(payment_id) DO NOTHING
            """, UUID.randomUUID(),request.getId(),paymentId,request.getBookingId(),request.getId());
        var row = locked(paymentId);
        if (inserted == 1) audit(row,"CREATED",actor);
        return row;
    }

    @Transactional
    public Map<String,Object> submit(UUID paymentId, String actor, String reference, String evidence) {
        if (reference == null || !reference.matches("[A-Za-z0-9._:/-]{1,200}")
                || evidence == null || !evidence.matches("[A-Za-z0-9._:-]{1,200}"))
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Bounded external reference and private evidence identifier required");
        requests.validateExecution(paymentId);
        var row=locked(paymentId);
        if ("PENDING_VERIFICATION".equals(row.get("state")) && reference.equals(row.get("external_reference"))
                && evidence.equals(row.get("evidence_reference"))) return row;
        if (!"PENDING_MANUAL_REFUND".equals(row.get("state")))
            throw new ResponseStatusException(HttpStatus.CONFLICT,"Evidence already submitted or workflow requires review");
        jdbc.update("UPDATE manual_refund_executions SET state='PENDING_VERIFICATION',external_reference=?,evidence_reference=?,submitted_by=?,submitted_at=now(),updated_at=now() WHERE payment_id=?",
                reference,evidence,required(actor),paymentId);
        audit(row,"EVIDENCE_SUBMITTED",actor);
        return locked(paymentId);
    }

    public List<Map<String,Object>> reviewQueue() {
        return jdbc.queryForList("SELECT id,payment_id,booking_id,state,created_at,updated_at FROM manual_refund_executions WHERE state IN ('PENDING_MANUAL_REFUND','PENDING_VERIFICATION','NEEDS_MANUAL_REVIEW') ORDER BY created_at,id LIMIT 100");
    }

    public void verify(UUID paymentId) {
        throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,"No trusted evidence verification source or approved verification policy is configured; financial state unchanged");
    }

    private Map<String,Object> locked(UUID paymentId) {
        var rows=jdbc.queryForList("SELECT * FROM manual_refund_executions WHERE payment_id=? FOR UPDATE",paymentId);
        if(rows.size()!=1) throw new ResponseStatusException(HttpStatus.NOT_FOUND,"Manual execution not found");
        return rows.getFirst();
    }
    private void audit(Map<String,Object> row,String action,String actor) {
        jdbc.update("INSERT INTO manual_refund_audit(id,execution_id,action,actor_id) VALUES (?,?,?,?)",UUID.randomUUID(),row.get("id"),action,actor);
    }
}

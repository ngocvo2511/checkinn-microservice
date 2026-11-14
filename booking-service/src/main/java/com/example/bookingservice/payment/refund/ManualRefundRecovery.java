package com.example.bookingservice.payment.refund;

import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.jdbc.core.JdbcTemplate;
/** Repairs missing internal records only; never executes an external refund. */
@Component
@RequiredArgsConstructor
public class ManualRefundRecovery {
 private final JdbcTemplate jdbc;
 private final ManualRefundService service;
 @Scheduled(fixedDelayString="${refund.manual.recovery-delay-ms:60000}",initialDelayString="${refund.manual.recovery-initial-delay-ms:60000}")
 public void recover() {
  for(UUID id:jdbc.queryForList("SELECT r.payment_id FROM refund_requests r LEFT JOIN manual_refund_executions e ON e.payment_id=r.payment_id WHERE r.status='APPROVED_PENDING_EXECUTION' AND e.id IS NULL ORDER BY r.approved_at LIMIT 20",UUID.class)) {
   try { service.start(id,"SYSTEM_RECOVERY"); }
   catch(RuntimeException unavailable) { /* Durable request remains for next scan/manual review. */ }
  }
 }
}

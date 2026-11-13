package com.example.bookingservice.payment.refund;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import static org.mockito.Mockito.*;
import static org.assertj.core.api.Assertions.*;
class ManualRefundServiceTest {
 @Test void verificationAlwaysFailsClosedWithoutTrustedSource() {
  var requests=mock(RefundRequestService.class); var jdbc=mock(JdbcTemplate.class);
  var service=new ManualRefundService(requests,jdbc);
  assertThatThrownBy(()->service.verify("p")).isInstanceOf(org.springframework.web.server.ResponseStatusException.class);
  verifyNoInteractions(requests,jdbc);
 }
 @Test void invalidEvidenceCannotAccessFinancialState() {
  var requests=mock(RefundRequestService.class); var jdbc=mock(JdbcTemplate.class);
  var service=new ManualRefundService(requests,jdbc);
  assertThatThrownBy(()->service.submit("p","admin","r","https://public.invalid/document"))
   .isInstanceOf(org.springframework.web.server.ResponseStatusException.class);
  verifyNoInteractions(requests,jdbc);
 }
 @Test void missingApprovalCannotCreateExecution() {
  var requests=mock(RefundRequestService.class); var jdbc=mock(JdbcTemplate.class);
  doThrow(new IllegalStateException("approval missing")).when(requests).validateExecution("p");
  assertThatThrownBy(()->new ManualRefundService(requests,jdbc).start("p","admin")).isInstanceOf(IllegalStateException.class);
  verifyNoInteractions(jdbc);
 }
}

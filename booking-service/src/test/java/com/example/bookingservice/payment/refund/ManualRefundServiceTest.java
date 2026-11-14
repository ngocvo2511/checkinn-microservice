package com.example.bookingservice.payment.refund;
import static com.example.bookingservice.contract.TestIds.*;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import static org.mockito.Mockito.*;
import static org.assertj.core.api.Assertions.*;
class ManualRefundServiceTest {
 @Test void verificationAlwaysFailsClosedWithoutTrustedSource() {
  var requests=mock(RefundRequestService.class); var jdbc=mock(JdbcTemplate.class);
  var service=new ManualRefundService(requests,jdbc);
  assertThatThrownBy(()->service.verify(id("p"))).isInstanceOf(org.springframework.web.server.ResponseStatusException.class);
  verifyNoInteractions(requests,jdbc);
 }
 @Test void invalidEvidenceCannotAccessFinancialState() {
  var requests=mock(RefundRequestService.class); var jdbc=mock(JdbcTemplate.class);
  var service=new ManualRefundService(requests,jdbc);
  assertThatThrownBy(()->service.submit(id("p"),"admin","r","https://public.invalid/document"))
   .isInstanceOf(org.springframework.web.server.ResponseStatusException.class);
  verifyNoInteractions(requests,jdbc);
 }
 @Test void missingApprovalCannotCreateExecution() {
  var requests=mock(RefundRequestService.class); var jdbc=mock(JdbcTemplate.class);
  doThrow(new IllegalStateException("approval missing")).when(requests).validateExecution(id("p"));
  assertThatThrownBy(()->new ManualRefundService(requests,jdbc).start(id("p"),"admin")).isInstanceOf(IllegalStateException.class);
  verifyNoInteractions(jdbc);
 }
}

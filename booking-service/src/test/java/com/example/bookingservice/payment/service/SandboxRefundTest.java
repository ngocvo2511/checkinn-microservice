package com.example.bookingservice.payment.service;
import static com.example.bookingservice.contract.TestIds.*;
import org.junit.jupiter.api.Test;
import static org.mockito.Mockito.*;
import static org.assertj.core.api.Assertions.*;
import com.example.bookingservice.payment.refund.*;
import com.example.bookingservice.payment.entity.Payment;
import com.example.bookingservice.payment.enums.*;
import com.example.bookingservice.booking.entity.Booking;
import com.example.bookingservice.booking.enums.BookingStatus;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;
class SandboxRefundTest {
 @Test void internalRefundAndRetryPreserveOriginalReceiptAndPublishOnce() {
  var payments=mock(com.example.bookingservice.payment.repository.PaymentRepository.class);
  var bookings=mock(com.example.bookingservice.booking.repository.BookingRepository.class);
  var refunds=mock(RefundRequestService.class);
  var events=mock(com.example.bookingservice.messaging.HotelEventPublisher.class);
  var props=new com.example.bookingservice.payment.vnpay.VnPayProperties(); props.setPayUrl("https://sandbox.vnpayment.vn/paymentv2/vpcpay.html");
  var s=new PaymentService(payments,bookings,props,mock(com.example.bookingservice.booking.service.BookingService.class),events,mock(com.example.bookingservice.payment.service.factory.PaymentCreationPolicyFactory.class),mock(org.springframework.context.ApplicationEventPublisher.class),refunds);
  var jdbc=mock(org.springframework.jdbc.core.JdbcTemplate.class);
  org.springframework.test.util.ReflectionTestUtils.setField(s,"refundJdbc",jdbc);
  org.springframework.test.util.ReflectionTestUtils.setField(s,"sandboxRefundEnabled",true);
  var b=Booking.builder().id(id("b")).status(BookingStatus.CANCELLED).paidAmount(BigDecimal.TEN).checkInDate(LocalDate.of(2026,12,1)).checkOutDate(LocalDate.of(2026,12,2)).build();
  var p=Payment.builder().id(id("p")).bookingId(id("b")).method(PaymentMethod.VNPAY).status(PaymentStatus.COMPLETED).amount(BigDecimal.TEN).vnpayTransactionNo("original-receipt").build();
  var r=new RefundRequest();r.setId(id("r"));r.setPaymentId(id("p"));r.setBookingId(id("b"));r.setAmount(BigDecimal.TEN);r.setStatus(RefundRequest.Status.APPROVED_PENDING_EXECUTION);
  when(payments.findBookingIdById(id("p"))).thenReturn(Optional.of(id("b")));when(payments.findByIdForUpdate(id("p"))).thenReturn(Optional.of(p));when(bookings.findByIdForUpdate(id("b"))).thenReturn(Optional.of(b));when(refunds.get(id("p"))).thenReturn(r);
  var context=org.springframework.security.core.context.SecurityContextHolder.getContext();
  context.setAuthentication(new org.springframework.security.authentication.UsernamePasswordAuthenticationToken("admin",null,List.of(new org.springframework.security.core.authority.SimpleGrantedAuthority("ROLE_ADMIN"))));
  try {
   s.refundPayment(id("p"));s.refundPayment(id("p"));
   assertThat(b.getPaidAmount()).isEqualByComparingTo("0");assertThat(b.getStatus()).isEqualTo(BookingStatus.CANCELLED);
   assertThat(p.getStatus()).isEqualTo(PaymentStatus.REFUNDED);assertThat(p.getVnpayTransactionNo()).isEqualTo("original-receipt");
   assertThat(r.getExecutionMode()).isEqualTo("SANDBOX_INTERNAL");
   verify(events,times(1)).publishPaymentRefunded(argThat(e->e.getTransactionId().equals("sandbox-refund:"+id("r")) && e.getExecutionMode().equals("SANDBOX_INTERNAL")));
   org.springframework.test.util.ReflectionTestUtils.setField(s,"sandboxRefundEnabled",false);
   assertThatThrownBy(()->s.refundPayment(id("p"))).isInstanceOf(org.springframework.web.server.ResponseStatusException.class);
  } finally {org.springframework.security.core.context.SecurityContextHolder.clearContext();}
 }
 @Test void missingBookingReturns404BeforeFinancialMutation() {
  var payments=mock(com.example.bookingservice.payment.repository.PaymentRepository.class);
  var bookings=mock(com.example.bookingservice.booking.repository.BookingRepository.class);
  var events=mock(com.example.bookingservice.messaging.HotelEventPublisher.class);
  var refunds=mock(RefundRequestService.class);
  var service=new PaymentService(payments,bookings,new com.example.bookingservice.payment.vnpay.VnPayProperties(),mock(com.example.bookingservice.booking.service.BookingService.class),events,mock(com.example.bookingservice.payment.service.factory.PaymentCreationPolicyFactory.class),mock(org.springframework.context.ApplicationEventPublisher.class),refunds);
  org.springframework.test.util.ReflectionTestUtils.setField(service,"sandboxRefundEnabled",true);
  when(payments.findBookingIdById(id("orphan"))).thenReturn(Optional.of(id("missing")));
  when(bookings.findByIdForUpdate(id("missing"))).thenReturn(Optional.empty());
  org.springframework.security.core.context.SecurityContextHolder.getContext().setAuthentication(new org.springframework.security.authentication.UsernamePasswordAuthenticationToken("admin",null,List.of(new org.springframework.security.core.authority.SimpleGrantedAuthority("ROLE_ADMIN"))));
  try {
   assertThatThrownBy(()->service.refundPayment(id("orphan"))).isInstanceOfSatisfying(org.springframework.web.server.ResponseStatusException.class,e->assertThat(e.getStatusCode().value()).isEqualTo(404));
   verifyNoInteractions(events,refunds);
   verify(payments,never()).findByIdForUpdate(any(java.util.UUID.class));
  } finally {org.springframework.security.core.context.SecurityContextHolder.clearContext();}
 }
}

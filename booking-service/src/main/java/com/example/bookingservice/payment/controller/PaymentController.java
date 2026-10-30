package com.example.bookingservice.payment.controller;

import com.example.bookingservice.payment.dto.CreatePaymentRequest;
import com.example.bookingservice.payment.dto.PaymentResponse;
import com.example.bookingservice.payment.service.PaymentService;
import com.example.bookingservice.payment.dto.VnPayInitResponse;
import com.example.bookingservice.payment.vnpay.VnPayProperties;
import com.example.bookingservice.payment.vnpay.VnPayUtil;
import com.example.bookingservice.payment.repository.PaymentRepository;
import com.example.bookingservice.payment.enums.PaymentMethod;
import java.math.BigDecimal;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import java.util.Map;

@RestController
@RequestMapping("/api/payments")
@RequiredArgsConstructor
public class PaymentController {

    private final PaymentService paymentService;
    private final VnPayProperties vnPayProperties;
    private final PaymentRepository paymentRepository;

    @PostMapping
    @PreAuthorize("@bookingAccess.canPay(authentication, #request.bookingId)")
    public ResponseEntity<PaymentResponse> createPayment(@RequestBody CreatePaymentRequest request) {
        try {
            PaymentResponse payment = paymentService.createPayment(request);
            return ResponseEntity.status(HttpStatus.CREATED).body(payment);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().build();
        }
    }

    @PostMapping("/vnpay/create")
    @PreAuthorize("@bookingAccess.canPay(authentication, #bookingId)")
    public ResponseEntity<VnPayInitResponse> createVnPay(@RequestParam String bookingId, @RequestHeader(value = "X-Forwarded-For", required = false) String xff, @RequestHeader(value = "X-Real-IP", required = false) String xRealIp) {
        try {
            String clientIp = xff != null ? xff.split(",")[0].trim() : (xRealIp != null ? xRealIp : null);
            VnPayInitResponse resp = paymentService.initVnPayPayment(bookingId, clientIp);
            return ResponseEntity.ok(resp);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().build();
        }
    }

    @GetMapping("/vnpay/return")
    public ResponseEntity<PaymentResponse> vnpayReturn(@RequestParam Map<String, String> queryParams) {
        if (vnPayProperties.getHashSecret() == null || vnPayProperties.getHashSecret().isBlank()
                || vnPayProperties.getTmnCode() == null || vnPayProperties.getTmnCode().isBlank()) {
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).build();
        }
        // Verify secure hash
        String secureHash = queryParams.get("vnp_SecureHash");
        Map<String, String> paramsForHash = new java.util.HashMap<>(queryParams);
        paramsForHash.remove("vnp_SecureHash");
        paramsForHash.remove("vnp_SecureHashType");
        String data = VnPayUtil.buildQuery(paramsForHash);
        String expected = VnPayUtil.hmacSHA512(vnPayProperties.getHashSecret(), data);
        if (secureHash == null || !secureHash.equalsIgnoreCase(expected)) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
        }

        String orderId = queryParams.get("vnp_TxnRef");
        String responseCode = queryParams.get("vnp_ResponseCode");
        String transactionNo = queryParams.get("vnp_TransactionNo");
        String amount = queryParams.get("vnp_Amount");
        String transactionStatus = queryParams.get("vnp_TransactionStatus");
        if (!vnPayProperties.getTmnCode().equals(queryParams.get("vnp_TmnCode"))
                || orderId == null || orderId.isBlank()
                || responseCode == null || !responseCode.matches("[0-9]{2}")
                || transactionStatus == null || !transactionStatus.matches("[0-9]{2}")
                || amount == null || !amount.matches("[0-9]{1,12}")
                || transactionNo == null || !transactionNo.matches("[0-9]{1,15}")
                || ("00".equals(responseCode) && (!"00".equals(transactionStatus) || "0".equals(transactionNo)))) {
            return ResponseEntity.badRequest().build();
        }
        var payment = paymentRepository.findByVnpayOrderId(orderId).orElse(null);
        if (payment == null) return ResponseEntity.notFound().build();
        if (payment.getMethod() != PaymentMethod.VNPAY || payment.getAmount() == null
                || payment.getAmount().signum() <= 0
                || new BigDecimal(amount).compareTo(payment.getAmount().multiply(BigDecimal.valueOf(100))) != 0) {
            return ResponseEntity.badRequest().build();
        }
        try {
            PaymentResponse result = paymentService.processVNPayCallback(orderId, responseCode, transactionNo);
            return ResponseEntity.ok(result);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.notFound().build();
        }
    }

    @GetMapping("/{id}")
    @PreAuthorize("@bookingAccess.canReadPayment(authentication, #id)")
    public ResponseEntity<PaymentResponse> getPayment(@PathVariable String id) {
        try {
            PaymentResponse payment = paymentService.getPayment(id);
            return ResponseEntity.ok(payment);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.notFound().build();
        }
    }

    @GetMapping("/booking/{bookingId}")
    @PreAuthorize("@bookingAccess.canRead(authentication, #bookingId)")
    public ResponseEntity<PaymentResponse> getPaymentByBookingId(@PathVariable String bookingId) {
        try {
            PaymentResponse payment = paymentService.getPaymentByBookingId(bookingId);
            return ResponseEntity.ok(payment);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.notFound().build();
        }
    }

    @PostMapping("/{id}/refund")
    @PreAuthorize("@bookingAccess.isAdmin(authentication)")
    public ResponseEntity<PaymentResponse> refundPayment(@PathVariable String id) {
        try {
            PaymentResponse payment = paymentService.refundPayment(id);
            return ResponseEntity.ok(payment);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().build();
        }
    }

    @PostMapping("/{bookingId}/confirm")
    @PreAuthorize("@bookingAccess.canManage(authentication, #bookingId)")
    public ResponseEntity<PaymentResponse> confirmHotelPayment(@PathVariable String bookingId) {
        try {
            PaymentResponse payment = paymentService.confirmHotelPayment(bookingId);
            return ResponseEntity.ok(payment);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().build();
        }
    }

    // Legacy internal callback kept via gRPC server
}

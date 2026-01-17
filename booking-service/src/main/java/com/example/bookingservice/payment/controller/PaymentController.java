package com.example.bookingservice.payment.controller;

import com.example.bookingservice.payment.dto.CreatePaymentRequest;
import com.example.bookingservice.payment.dto.PaymentResponse;
import com.example.bookingservice.payment.service.PaymentService;
import com.example.bookingservice.payment.dto.VnPayInitResponse;
import com.example.bookingservice.payment.vnpay.VnPayProperties;
import com.example.bookingservice.payment.vnpay.VnPayUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.Map;

@RestController
@RequestMapping("/api/payments")
@RequiredArgsConstructor
public class PaymentController {

    private final PaymentService paymentService;
    private final VnPayProperties vnPayProperties;

    @PostMapping
    public ResponseEntity<PaymentResponse> createPayment(@RequestBody CreatePaymentRequest request) {
        try {
            PaymentResponse payment = paymentService.createPayment(request);
            return ResponseEntity.status(HttpStatus.CREATED).body(payment);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().build();
        }
    }

    @PostMapping("/vnpay/create")
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
        try {
            PaymentResponse payment = paymentService.processVNPayCallback(orderId, responseCode, transactionNo);
            return ResponseEntity.ok(payment);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.notFound().build();
        }
    }

    @GetMapping("/{id}")
    public ResponseEntity<PaymentResponse> getPayment(@PathVariable String id) {
        try {
            PaymentResponse payment = paymentService.getPayment(id);
            return ResponseEntity.ok(payment);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.notFound().build();
        }
    }

    @GetMapping("/booking/{bookingId}")
    public ResponseEntity<PaymentResponse> getPaymentByBookingId(@PathVariable String bookingId) {
        try {
            PaymentResponse payment = paymentService.getPaymentByBookingId(bookingId);
            return ResponseEntity.ok(payment);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.notFound().build();
        }
    }

    @PostMapping("/{id}/refund")
    public ResponseEntity<PaymentResponse> refundPayment(@PathVariable String id) {
        try {
            PaymentResponse payment = paymentService.refundPayment(id);
            return ResponseEntity.ok(payment);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().build();
        }
    }

    @PostMapping("/{bookingId}/confirm")
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

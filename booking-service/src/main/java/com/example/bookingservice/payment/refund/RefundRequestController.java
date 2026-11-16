package com.example.bookingservice.payment.refund;

import static com.example.bookingservice.contract.UuidContract.*;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/payments/{paymentId}/refund-request")
@RequiredArgsConstructor
public class RefundRequestController {
    private final RefundRequestService refunds;
    public record Request(String reason) {}
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("@bookingAccess.canReadPayment(authentication, #paymentId)")
    public RefundRequest request(@PathVariable java.util.UUID paymentId, @RequestBody Request request, Authentication actor) {
        return refunds.request(paymentId, required(actor.getName()), request.reason());
    }
    @GetMapping
    @PreAuthorize("@bookingAccess.canReadPayment(authentication, #paymentId)")
    public RefundRequest get(@PathVariable java.util.UUID paymentId) { return refunds.get(paymentId); }
    @PostMapping("/approve")
    @PreAuthorize("@bookingAccess.isAdmin(authentication)")
    public RefundRequest approve(@PathVariable java.util.UUID paymentId, Authentication actor) {
        return refunds.approve(paymentId, required(actor.getName()));
    }
    @PostMapping("/reject")
    @PreAuthorize("@bookingAccess.isAdmin(authentication)")
    public RefundRequest reject(@PathVariable java.util.UUID paymentId, Authentication actor) { return refunds.reject(paymentId,actor.getName()); }
}

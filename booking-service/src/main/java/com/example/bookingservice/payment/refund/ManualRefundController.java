package com.example.bookingservice.payment.refund;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import java.util.*;
@RestController
@RequestMapping("/api/payments/manual-refunds")
@RequiredArgsConstructor
@PreAuthorize("@bookingAccess.isAdmin(authentication)")
public class ManualRefundController {
    private final ManualRefundService service;
    public record Evidence(String externalReference,String evidenceReference) {}
    @GetMapping public List<Map<String,Object>> queue() { return service.reviewQueue(); }
    @PostMapping("/{paymentId}/start") public Map<String,Object> start(@PathVariable String paymentId,Authentication actor) { return service.start(paymentId,actor.getName()); }
    @PostMapping("/{paymentId}/evidence") public Map<String,Object> submit(@PathVariable String paymentId,@RequestBody Evidence body,Authentication actor) { return service.submit(paymentId,actor.getName(),body.externalReference(),body.evidenceReference()); }
    @PostMapping("/{paymentId}/verify") public void verify(@PathVariable String paymentId) { service.verify(paymentId); }
}

package com.example.bookingservice.payment.service.factory;

import com.example.bookingservice.payment.enums.PaymentMethod;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

@Component
@RequiredArgsConstructor
public class PaymentCreationPolicyFactory {

    private final List<PaymentCreationPolicy> policies;
    private final Map<PaymentMethod, PaymentCreationPolicy> policyMap = new EnumMap<>(PaymentMethod.class);

    @PostConstruct
    void initializePolicyMap() {
        for (PaymentCreationPolicy policy : policies) {
            policyMap.put(policy.getMethod(), policy);
        }
    }

    public PaymentCreationPolicy getPolicy(PaymentMethod method) {
        PaymentCreationPolicy policy = policyMap.get(method);
        if (policy == null) {
            throw new IllegalArgumentException("Unsupported payment method: " + method);
        }
        return policy;
    }
}

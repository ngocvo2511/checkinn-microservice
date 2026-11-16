package com.example.bookingservice.payment.refund;

import java.util.UUID;
import java.math.BigDecimal;
/** Future adapter contract; no production implementation or automatic invocation. */
public interface RefundProvider {
    enum Outcome { SUCCESS, FAILURE, UNKNOWN }
    record Command(String commandId,UUID paymentId,BigDecimal amount,String currency) {}
    record Result(Outcome outcome,String externalReference) {}
    Result execute(Command command);
    Result query(Command command);
}

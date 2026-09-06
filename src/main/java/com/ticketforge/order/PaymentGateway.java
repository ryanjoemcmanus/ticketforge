package com.ticketforge.order;

import java.math.BigDecimal;

public interface PaymentGateway {
  PaymentResult charge(BigDecimal amount, String paymentToken, String idempotencyKey);

  RefundResult refund(String paymentReference, BigDecimal amount);

  record PaymentResult(String reference) {}

  record RefundResult(String reference) {}
}

package com.ticketforge.order;

import com.ticketforge.common.ApiException;
import java.math.BigDecimal;
import java.util.UUID;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(
    name = "ticketforge.payments.provider",
    havingValue = "mock",
    matchIfMissing = true)
public class MockPaymentGateway implements PaymentGateway {
  public PaymentResult charge(BigDecimal amount, String token, String idempotencyKey) {
    if ("tok_decline".equals(token))
      throw new ApiException(HttpStatus.PAYMENT_REQUIRED, "Payment was declined");
    if (token == null || !token.startsWith("tok_"))
      throw new IllegalArgumentException("Use a mock payment token beginning with tok_");
    return new PaymentResult("pay_" + UUID.randomUUID().toString().replace("-", ""));
  }

  public RefundResult refund(String paymentReference, BigDecimal amount) {
    if (paymentReference == null || !paymentReference.startsWith("pay_"))
      throw new IllegalArgumentException("Unknown payment reference");
    return new RefundResult("refund_" + UUID.randomUUID().toString().replace("-", ""));
  }
}

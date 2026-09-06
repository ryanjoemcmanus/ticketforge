package com.ticketforge.order;

import static org.assertj.core.api.Assertions.*;

import com.ticketforge.common.ApiException;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

class MockPaymentGatewayTest {
  private final MockPaymentGateway gateway = new MockPaymentGateway();

  @Test
  void acceptsTokenizedPaymentWithoutHandlingCardData() {
    assertThat(gateway.charge(new BigDecimal("42.50"), "tok_visa", "payment-1").reference())
        .startsWith("pay_");
  }

  @Test
  void declinesDeterministicTestToken() {
    assertThatThrownBy(() -> gateway.charge(BigDecimal.TEN, "tok_decline", "payment-2"))
        .isInstanceOf(ApiException.class)
        .hasMessage("Payment was declined");
  }

  @Test
  void rejectsRawOrMalformedPaymentInput() {
    assertThatThrownBy(() -> gateway.charge(BigDecimal.TEN, "4111111111111111", "payment-3"))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void refundsKnownPaymentReference() {
    assertThat(gateway.refund("pay_123", BigDecimal.TEN).reference()).startsWith("refund_");
  }
}

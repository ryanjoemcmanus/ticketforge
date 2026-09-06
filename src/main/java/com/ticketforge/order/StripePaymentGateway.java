package com.ticketforge.order;

import com.stripe.StripeClient;
import com.stripe.exception.StripeException;
import com.stripe.model.PaymentIntent;
import com.stripe.net.RequestOptions;
import com.stripe.param.PaymentIntentCreateParams;
import com.stripe.param.RefundCreateParams;
import com.ticketforge.common.ApiException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Locale;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "ticketforge.payments.provider", havingValue = "stripe")
public class StripePaymentGateway implements PaymentGateway {
  private final StripeClient client;
  private final String currency;

  public StripePaymentGateway(
      @Value("${ticketforge.payments.stripe-secret-key}") String secretKey,
      @Value("${ticketforge.payments.currency:usd}") String currency) {
    if (!secretKey.startsWith("sk_test_")) {
      throw new IllegalStateException(
          "STRIPE_SECRET_KEY must be a Stripe test key; live keys are intentionally rejected");
    }
    this.client = new StripeClient(secretKey);
    this.currency = currency.toLowerCase(Locale.ROOT);
  }

  @Override
  public PaymentResult charge(BigDecimal amount, String paymentMethod, String idempotencyKey) {
    try {
      var params =
          PaymentIntentCreateParams.builder()
              .setAmount(minorUnits(amount))
              .setCurrency(currency)
              .setPaymentMethod(paymentMethod)
              .addPaymentMethodType("card")
              .setConfirm(true)
              .setErrorOnRequiresAction(true)
              .build();
      RequestOptions options = RequestOptions.builder().setIdempotencyKey(idempotencyKey).build();
      PaymentIntent intent = client.v1().paymentIntents().create(params, options);
      if (!"succeeded".equals(intent.getStatus())) {
        throw new ApiException(HttpStatus.PAYMENT_REQUIRED, "Payment was not completed");
      }
      return new PaymentResult(intent.getId());
    } catch (StripeException e) {
      throw new ApiException(HttpStatus.PAYMENT_REQUIRED, "Payment provider declined the request");
    }
  }

  @Override
  public RefundResult refund(String paymentReference, BigDecimal amount) {
    try {
      var params =
          RefundCreateParams.builder()
              .setPaymentIntent(paymentReference)
              .setAmount(minorUnits(amount))
              .setReason(RefundCreateParams.Reason.REQUESTED_BY_CUSTOMER)
              .build();
      var refund = client.v1().refunds().create(params);
      return new RefundResult(refund.getId());
    } catch (StripeException e) {
      throw new ApiException(HttpStatus.BAD_GATEWAY, "Payment provider could not issue the refund");
    }
  }

  private long minorUnits(BigDecimal amount) {
    return amount.movePointRight(2).setScale(0, RoundingMode.UNNECESSARY).longValueExact();
  }
}

package com.ticketforge.order;

import com.stripe.model.Charge;
import com.stripe.model.Event;
import com.stripe.model.PaymentIntent;
import com.stripe.model.StripeObject;
import com.stripe.net.Webhook;
import com.ticketforge.common.ApiException;
import com.ticketforge.common.AuditEvent;
import com.ticketforge.common.AuditEventRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;

@Controller
@ConditionalOnProperty(name = "ticketforge.payments.provider", havingValue = "stripe")
public class StripeWebhookController {
  private static final Logger log = LoggerFactory.getLogger(StripeWebhookController.class);
  private final String webhookSecret;
  private final PaymentWebhookReceiptRepository receipts;
  private final OrderRepository orders;
  private final AuditEventRepository audit;

  public StripeWebhookController(
      @Value("${ticketforge.payments.stripe-webhook-secret}") String webhookSecret,
      PaymentWebhookReceiptRepository receipts,
      OrderRepository orders,
      AuditEventRepository audit) {
    if (!webhookSecret.startsWith("whsec_")) {
      throw new IllegalStateException("STRIPE_WEBHOOK_SECRET must be configured in Stripe mode");
    }
    this.webhookSecret = webhookSecret;
    this.receipts = receipts;
    this.orders = orders;
    this.audit = audit;
  }

  @PostMapping("/api/v1/payments/stripe/webhook")
  @Transactional
  public ResponseEntity<Void> receive(
      @RequestBody String payload, @RequestHeader("Stripe-Signature") String signature) {
    if (payload.length() > 1_000_000) {
      throw new ApiException(HttpStatus.PAYLOAD_TOO_LARGE, "Stripe webhook payload is too large");
    }
    Event event;
    try {
      event = Webhook.constructEvent(payload, signature, webhookSecret);
    } catch (Exception e) {
      throw new ApiException(HttpStatus.BAD_REQUEST, "Invalid Stripe webhook signature");
    }
    if (receipts.existsByProviderEventId(event.getId())) return ResponseEntity.ok().build();

    StripeObject object = event.getDataObjectDeserializer().getObject().orElse(null);
    String paymentReference = reference(object);
    receipts.save(new PaymentWebhookReceipt(event.getId(), event.getType(), paymentReference));
    if (paymentReference != null) {
      orders
          .findByPaymentReference(paymentReference)
          .ifPresent(
              order -> {
                audit.save(
                    new AuditEvent(
                        null, "PAYMENT_WEBHOOK_RECEIVED", "ORDER", order.getId(), event.getType()));
                log.info(
                    "stripe_webhook_reconciled eventId={} type={} orderId={}",
                    event.getId(),
                    event.getType(),
                    order.getId());
              });
    }
    return ResponseEntity.ok().build();
  }

  private String reference(StripeObject object) {
    if (object instanceof PaymentIntent intent) return intent.getId();
    if (object instanceof Charge charge) return charge.getPaymentIntent();
    return null;
  }
}

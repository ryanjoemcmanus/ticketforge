package com.ticketforge.order;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "payment_webhook_receipts")
public class PaymentWebhookReceipt {
  @Id private UUID id;

  @Column(name = "provider_event_id", nullable = false, unique = true, length = 255)
  private String providerEventId;

  @Column(name = "event_type", nullable = false, length = 120)
  private String eventType;

  @Column(name = "payment_reference", length = 120)
  private String paymentReference;

  @Column(name = "received_at", nullable = false)
  private Instant receivedAt;

  protected PaymentWebhookReceipt() {}

  public PaymentWebhookReceipt(String providerEventId, String eventType, String paymentReference) {
    this.id = UUID.randomUUID();
    this.providerEventId = providerEventId;
    this.eventType = eventType;
    this.paymentReference = paymentReference;
    this.receivedAt = Instant.now();
  }
}

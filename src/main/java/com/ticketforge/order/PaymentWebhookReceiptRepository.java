package com.ticketforge.order;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PaymentWebhookReceiptRepository
    extends JpaRepository<PaymentWebhookReceipt, UUID> {
  boolean existsByProviderEventId(String providerEventId);
}

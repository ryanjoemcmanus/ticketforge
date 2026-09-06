package com.ticketforge.notification;

import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
public class NotificationEventHandler {
  private static final Logger log = LoggerFactory.getLogger(NotificationEventHandler.class);
  private final NotificationGateway gateway;
  private final String appUrl;

  public NotificationEventHandler(
      NotificationGateway gateway, @Value("${ticketforge.app-url}") String appUrl) {
    this.gateway = gateway;
    this.appUrl = appUrl.replaceAll("/+$", "");
  }

  @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
  public void onVerify(NotificationEvents.VerifyEmail event) {
    safeSend(
        event.email(),
        "Verify your TicketForge account",
        "Hi "
            + event.displayName()
            + ",\n\nVerify your account: "
            + appUrl
            + "/?verify="
            + event.token()
            + "\n\nThis link expires in 24 hours.");
  }

  @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
  public void onReset(NotificationEvents.ResetPassword event) {
    safeSend(
        event.email(),
        "Reset your TicketForge password",
        "Hi "
            + event.displayName()
            + ",\n\nReset your password: "
            + appUrl
            + "/?reset="
            + event.token()
            + "\n\nThis link expires in 30 minutes.");
  }

  @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
  public void onOrder(NotificationEvents.OrderConfirmed event) {
    safeSend(
        event.email(),
        "Your TicketForge order is confirmed",
        "Hi "
            + event.displayName()
            + ",\n\nOrder "
            + event.orderId()
            + " is confirmed for $"
            + event.total()
            + ".\nTickets:\n"
            + event.ticketCodes().stream()
                .map(code -> "- " + code)
                .collect(Collectors.joining("\n")));
  }

  @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
  public void onRefund(NotificationEvents.OrderRefunded event) {
    safeSend(
        event.email(),
        "Your TicketForge order was refunded",
        "Hi "
            + event.displayName()
            + ",\n\nOrder "
            + event.orderId()
            + " was cancelled and $"
            + event.total()
            + " was refunded.");
  }

  private void safeSend(String recipient, String subject, String body) {
    try {
      gateway.send(recipient, subject, body);
    } catch (RuntimeException e) {
      log.error("notification_delivery_failed recipient={} subject={}", recipient, subject, e);
    }
  }
}

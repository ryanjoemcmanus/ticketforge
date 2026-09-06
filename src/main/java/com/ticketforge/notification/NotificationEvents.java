package com.ticketforge.notification;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

public final class NotificationEvents {
  private NotificationEvents() {}

  public record VerifyEmail(String email, String displayName, String token) {}

  public record ResetPassword(String email, String displayName, String token) {}

  public record OrderConfirmed(
      String email, String displayName, UUID orderId, BigDecimal total, List<String> ticketCodes) {}

  public record OrderRefunded(String email, String displayName, UUID orderId, BigDecimal total) {}
}

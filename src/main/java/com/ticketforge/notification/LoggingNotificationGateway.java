package com.ticketforge.notification;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(
    name = "ticketforge.notifications.provider",
    havingValue = "log",
    matchIfMissing = true)
public class LoggingNotificationGateway implements NotificationGateway {
  private static final Logger log = LoggerFactory.getLogger(LoggingNotificationGateway.class);

  @Override
  public void send(String recipient, String subject, String body) {
    // Bodies can contain action tokens or ticket codes. Keep even local logs metadata-only.
    log.info(
        "notification_captured recipient={} subject={} bodyLength={}",
        recipient,
        subject,
        body.length());
  }
}

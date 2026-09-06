package com.ticketforge.notification;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "ticketforge.notifications.provider", havingValue = "smtp")
public class SmtpNotificationGateway implements NotificationGateway {
  private final JavaMailSender sender;
  private final String from;

  public SmtpNotificationGateway(
      JavaMailSender sender, @Value("${ticketforge.notifications.from}") String from) {
    this.sender = sender;
    this.from = from;
  }

  @Override
  public void send(String recipient, String subject, String body) {
    var message = new SimpleMailMessage();
    message.setFrom(from);
    message.setTo(recipient);
    message.setSubject(subject);
    message.setText(body);
    sender.send(message);
  }
}

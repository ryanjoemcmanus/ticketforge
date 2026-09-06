package com.ticketforge.notification;

public interface NotificationGateway {
  void send(String recipient, String subject, String body);
}

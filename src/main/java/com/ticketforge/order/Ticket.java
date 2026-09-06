package com.ticketforge.order;

import com.ticketforge.catalog.EventSeat;
import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "tickets")
public class Ticket {
  @Id private UUID id;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "order_id")
  private CustomerOrder order;

  @OneToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "event_seat_id", unique = true)
  private EventSeat eventSeat;

  @Column(name = "ticket_code", nullable = false, unique = true, length = 80)
  private String ticketCode;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 20)
  private TicketStatus status;

  @Column(name = "issued_at", nullable = false)
  private Instant issuedAt;

  @Column(name = "cancelled_at")
  private Instant cancelledAt;

  protected Ticket() {}

  public Ticket(CustomerOrder order, EventSeat eventSeat) {
    this.id = UUID.randomUUID();
    this.order = order;
    this.eventSeat = eventSeat;
    this.ticketCode = "TKT-" + UUID.randomUUID().toString().replace("-", "").toUpperCase();
    this.status = TicketStatus.ISSUED;
    this.issuedAt = Instant.now();
  }

  public UUID getId() {
    return id;
  }

  public CustomerOrder getOrder() {
    return order;
  }

  public EventSeat getEventSeat() {
    return eventSeat;
  }

  public String getTicketCode() {
    return ticketCode;
  }

  public TicketStatus getStatus() {
    return status;
  }

  public Instant getIssuedAt() {
    return issuedAt;
  }

  public void cancel() {
    if (status == TicketStatus.ISSUED) {
      status = TicketStatus.CANCELLED;
      cancelledAt = Instant.now();
    }
  }

  public Instant getCancelledAt() {
    return cancelledAt;
  }
}

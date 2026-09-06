package com.ticketforge.reservation;

import com.ticketforge.catalog.Event;
import com.ticketforge.user.UserAccount;
import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "seat_holds")
public class SeatHold {
  @Id private UUID id;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "customer_id")
  private UserAccount customer;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "event_id")
  private Event event;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 20)
  private HoldStatus status;

  @Column(name = "expires_at", nullable = false)
  private Instant expiresAt;

  @Column(name = "created_at", nullable = false)
  private Instant createdAt;

  @Version private long version;

  protected SeatHold() {}

  public SeatHold(UserAccount customer, Event event, Instant expiresAt) {
    this.id = UUID.randomUUID();
    this.customer = customer;
    this.event = event;
    this.status = HoldStatus.ACTIVE;
    this.expiresAt = expiresAt;
    this.createdAt = Instant.now();
  }

  public boolean expired(Instant now) {
    return status == HoldStatus.ACTIVE && !expiresAt.isAfter(now);
  }

  public void expire() {
    if (status == HoldStatus.ACTIVE) status = HoldStatus.EXPIRED;
  }

  public void cancel() {
    if (status == HoldStatus.ACTIVE) status = HoldStatus.CANCELLED;
  }

  public void checkout() {
    if (status != HoldStatus.ACTIVE) throw new IllegalStateException("Hold is not active");
    status = HoldStatus.CHECKED_OUT;
  }

  public UUID getId() {
    return id;
  }

  public UserAccount getCustomer() {
    return customer;
  }

  public Event getEvent() {
    return event;
  }

  public HoldStatus getStatus() {
    return status;
  }

  public Instant getExpiresAt() {
    return expiresAt;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }
}

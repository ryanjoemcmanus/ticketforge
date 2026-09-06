package com.ticketforge.catalog;

import com.ticketforge.reservation.SeatHold;
import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(
    name = "event_seats",
    uniqueConstraints = @UniqueConstraint(columnNames = {"event_id", "seat_id"}))
public class EventSeat {
  @Id private UUID id;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "event_id")
  private Event event;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "seat_id")
  private Seat seat;

  @Column(nullable = false, precision = 12, scale = 2)
  private BigDecimal price;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 20)
  private EventSeatStatus status;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "hold_id")
  private SeatHold hold;

  @Column(name = "hold_expires_at")
  private Instant holdExpiresAt;

  @Version private long version;

  protected EventSeat() {}

  public EventSeat(Event event, Seat seat, BigDecimal price) {
    this.id = UUID.randomUUID();
    this.event = event;
    this.seat = seat;
    this.price = price;
    this.status = EventSeatStatus.AVAILABLE;
  }

  public void placeHold(SeatHold hold) {
    this.status = EventSeatStatus.HELD;
    this.hold = hold;
    this.holdExpiresAt = hold.getExpiresAt();
  }

  public void release() {
    this.status = EventSeatStatus.AVAILABLE;
    this.hold = null;
    this.holdExpiresAt = null;
  }

  public void sell() {
    this.status = EventSeatStatus.SOLD;
    this.hold = null;
    this.holdExpiresAt = null;
  }

  public void reprice(BigDecimal price) {
    if (status != EventSeatStatus.AVAILABLE)
      throw new IllegalStateException("Only available inventory can be repriced");
    this.price = price;
  }

  public boolean heldBy(UUID holdId) {
    return hold != null && hold.getId().equals(holdId);
  }

  public boolean holdExpired(Instant now) {
    return status == EventSeatStatus.HELD && holdExpiresAt != null && !holdExpiresAt.isAfter(now);
  }

  public UUID getId() {
    return id;
  }

  public Event getEvent() {
    return event;
  }

  public Seat getSeat() {
    return seat;
  }

  public BigDecimal getPrice() {
    return price;
  }

  public EventSeatStatus getStatus() {
    return status;
  }

  public SeatHold getHold() {
    return hold;
  }

  public Instant getHoldExpiresAt() {
    return holdExpiresAt;
  }
}

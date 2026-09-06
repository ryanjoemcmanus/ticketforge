package com.ticketforge.order;

import com.ticketforge.reservation.SeatHold;
import com.ticketforge.user.UserAccount;
import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(
    name = "orders",
    uniqueConstraints = @UniqueConstraint(columnNames = {"customer_id", "idempotency_key"}))
public class CustomerOrder {
  @Id private UUID id;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "customer_id")
  private UserAccount customer;

  @OneToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "hold_id", unique = true)
  private SeatHold hold;

  @Column(name = "idempotency_key", nullable = false, length = 100)
  private String idempotencyKey;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 30)
  private OrderStatus status;

  @Column(name = "total_amount", nullable = false, precision = 12, scale = 2)
  private BigDecimal totalAmount;

  @Column(name = "payment_reference", nullable = false, length = 120)
  private String paymentReference;

  @Column(name = "created_at", nullable = false)
  private Instant createdAt;

  @Column(name = "refunded_at")
  private Instant refundedAt;

  @Column(name = "cancellation_reason", length = 500)
  private String cancellationReason;

  protected CustomerOrder() {}

  public CustomerOrder(
      UserAccount customer, SeatHold hold, String key, BigDecimal total, String paymentReference) {
    this.id = UUID.randomUUID();
    this.customer = customer;
    this.hold = hold;
    this.idempotencyKey = key;
    this.status = OrderStatus.PAYMENT_CONFIRMED;
    this.totalAmount = total;
    this.paymentReference = paymentReference;
    this.createdAt = Instant.now();
  }

  public UUID getId() {
    return id;
  }

  public UserAccount getCustomer() {
    return customer;
  }

  public SeatHold getHold() {
    return hold;
  }

  public String getIdempotencyKey() {
    return idempotencyKey;
  }

  public OrderStatus getStatus() {
    return status;
  }

  public BigDecimal getTotalAmount() {
    return totalAmount;
  }

  public String getPaymentReference() {
    return paymentReference;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }

  public void refund(String reason) {
    if (status != OrderStatus.PAYMENT_CONFIRMED)
      throw new IllegalStateException("Order is not refundable");
    status = OrderStatus.REFUNDED;
    refundedAt = Instant.now();
    cancellationReason = reason;
  }

  public Instant getRefundedAt() {
    return refundedAt;
  }

  public String getCancellationReason() {
    return cancellationReason;
  }
}

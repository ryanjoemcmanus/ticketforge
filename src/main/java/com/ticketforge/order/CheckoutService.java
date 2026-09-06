package com.ticketforge.order;

import com.ticketforge.catalog.*;
import com.ticketforge.common.*;
import com.ticketforge.notification.NotificationEvents;
import com.ticketforge.reservation.*;
import com.ticketforge.user.*;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import org.slf4j.*;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CheckoutService {
  private static final Logger log = LoggerFactory.getLogger(CheckoutService.class);
  private final SeatHoldRepository holds;
  private final EventSeatRepository inventory;
  private final OrderRepository orders;
  private final TicketRepository tickets;
  private final PaymentGateway payments;
  private final UserRepository users;
  private final AuditEventRepository audit;
  private final ApplicationEventPublisher events;

  CheckoutService(
      SeatHoldRepository holds,
      EventSeatRepository inventory,
      OrderRepository orders,
      TicketRepository tickets,
      PaymentGateway payments,
      UserRepository users,
      AuditEventRepository audit,
      ApplicationEventPublisher events) {
    this.holds = holds;
    this.inventory = inventory;
    this.orders = orders;
    this.tickets = tickets;
    this.payments = payments;
    this.users = users;
    this.audit = audit;
    this.events = events;
  }

  @Transactional
  public CheckoutController.OrderView cancel(UUID customerId, UUID orderId, String reason) {
    CustomerOrder initial =
        orders.findById(orderId).orElseThrow(() -> ApiException.notFound("Order not found"));
    if (!initial.getCustomer().getId().equals(customerId))
      throw ApiException.forbidden("Order belongs to another customer");
    List<Ticket> issued = tickets.findDetailedByOrderId(orderId);
    List<EventSeat> locked =
        inventory.findAllLocked(issued.stream().map(t -> t.getEventSeat().getId()).toList());
    CustomerOrder order =
        orders.findLocked(orderId).orElseThrow(() -> ApiException.notFound("Order not found"));
    if (order.getStatus() != OrderStatus.PAYMENT_CONFIRMED)
      throw ApiException.conflict("Order is not refundable");
    Instant earliest =
        locked.stream().map(s -> s.getEvent().getStartsAt()).min(Instant::compareTo).orElseThrow();
    if (!earliest.isAfter(Instant.now().plus(Duration.ofHours(24))))
      throw ApiException.conflict("Refund window closed 24 hours before the event");
    payments.refund(order.getPaymentReference(), order.getTotalAmount());
    issued.forEach(Ticket::cancel);
    locked.forEach(EventSeat::release);
    order.refund(reason);
    audit.save(new AuditEvent(customerId, "ORDER_REFUNDED", "ORDER", orderId, reason));
    events.publishEvent(
        new NotificationEvents.OrderRefunded(
            order.getCustomer().getEmail(),
            order.getCustomer().getDisplayName(),
            orderId,
            order.getTotalAmount()));
    log.info(
        "order_refunded orderId={} customerId={} seatCount={}", orderId, customerId, locked.size());
    return view(order);
  }

  @Transactional
  public CheckoutController.OrderView checkout(
      UUID customerId, String key, CheckoutController.CheckoutRequest r) {
    if (key == null || key.isBlank() || key.length() > 100)
      throw new IllegalArgumentException(
          "Idempotency-Key header is required and must be at most 100 characters");
    Optional<CustomerOrder> existing = orders.findByCustomerIdAndIdempotencyKey(customerId, key);
    if (existing.isPresent()) return view(existing.get());
    List<EventSeat> locked = inventory.findByHoldIdLocked(r.holdId());
    SeatHold hold =
        holds.findLocked(r.holdId()).orElseThrow(() -> ApiException.notFound("Hold not found"));
    if (!hold.getCustomer().getId().equals(customerId))
      throw ApiException.forbidden("Hold belongs to another customer");
    existing = orders.findByCustomerIdAndIdempotencyKey(customerId, key);
    if (existing.isPresent()) return view(existing.get());
    if (hold.getStatus() != HoldStatus.ACTIVE) throw ApiException.conflict("Hold is not active");
    if (!hold.getExpiresAt().isAfter(Instant.now()))
      throw ApiException.conflict("Hold has expired");
    if (locked.isEmpty() || locked.stream().anyMatch(s -> !s.heldBy(hold.getId())))
      throw ApiException.conflict("Hold inventory is no longer valid");
    BigDecimal total =
        locked.stream().map(EventSeat::getPrice).reduce(BigDecimal.ZERO, BigDecimal::add);
    PaymentGateway.PaymentResult payment =
        payments.charge(total, r.paymentToken(), customerId + ":" + key);
    UserAccount customer = users.getReferenceById(customerId);
    CustomerOrder order =
        orders.save(new CustomerOrder(customer, hold, key, total, payment.reference()));
    locked.forEach(EventSeat::sell);
    hold.checkout();
    List<Ticket> issued = tickets.saveAll(locked.stream().map(s -> new Ticket(order, s)).toList());
    events.publishEvent(
        new NotificationEvents.OrderConfirmed(
            customer.getEmail(),
            customer.getDisplayName(),
            order.getId(),
            total,
            issued.stream().map(Ticket::getTicketCode).toList()));
    log.info(
        "checkout_completed orderId={} customerId={} holdId={} total={} ticketCount={}",
        order.getId(),
        customerId,
        hold.getId(),
        total,
        locked.size());
    return view(order);
  }

  @Transactional(readOnly = true)
  public List<CheckoutController.OrderView> list(UUID customerId) {
    return orders.findByCustomerIdOrderByCreatedAtDesc(customerId).stream()
        .map(this::view)
        .toList();
  }

  @Transactional(readOnly = true)
  public CheckoutController.OrderView get(UUID customerId, UUID orderId) {
    CustomerOrder o =
        orders.findById(orderId).orElseThrow(() -> ApiException.notFound("Order not found"));
    if (!o.getCustomer().getId().equals(customerId))
      throw ApiException.forbidden("Order belongs to another customer");
    return view(o);
  }

  private CheckoutController.OrderView view(CustomerOrder o) {
    List<CheckoutController.TicketView> ts =
        tickets.findDetailedByOrderId(o.getId()).stream()
            .map(
                t ->
                    new CheckoutController.TicketView(
                        t.getId(),
                        t.getTicketCode(),
                        t.getStatus(),
                        t.getEventSeat().getEvent().getId(),
                        t.getEventSeat().getId(),
                        t.getEventSeat().getSeat().getSectionName(),
                        t.getEventSeat().getSeat().getRowLabel(),
                        t.getEventSeat().getSeat().getSeatNumber()))
            .toList();
    return new CheckoutController.OrderView(
        o.getId(),
        o.getStatus(),
        o.getTotalAmount(),
        o.getPaymentReference(),
        o.getCreatedAt(),
        ts);
  }
}

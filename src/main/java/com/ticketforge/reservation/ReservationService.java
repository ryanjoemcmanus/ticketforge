package com.ticketforge.reservation;

import com.ticketforge.catalog.*;
import com.ticketforge.common.*;
import com.ticketforge.user.*;
import java.time.*;
import java.util.*;
import org.slf4j.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;

@Service
public class ReservationService {
  private static final Logger log = LoggerFactory.getLogger(ReservationService.class);
  private final EventSeatRepository inventory;
  private final SeatHoldRepository holds;
  private final UserRepository users;
  private final Duration duration;
  private final Clock clock;

  @Autowired
  public ReservationService(
      EventSeatRepository inventory,
      SeatHoldRepository holds,
      UserRepository users,
      @Value("${ticketforge.holds.duration}") Duration duration) {
    this(inventory, holds, users, duration, Clock.systemUTC());
  }

  ReservationService(
      EventSeatRepository inventory,
      SeatHoldRepository holds,
      UserRepository users,
      Duration duration,
      Clock clock) {
    this.inventory = inventory;
    this.holds = holds;
    this.users = users;
    this.duration = duration;
    this.clock = clock;
  }

  @Transactional
  public ReservationController.HoldView create(
      UUID customerId, ReservationController.CreateHold r) {
    List<UUID> ids = r.eventSeatIds().stream().distinct().sorted().toList();
    if (ids.size() != r.eventSeatIds().size())
      throw new IllegalArgumentException("Duplicate event seat IDs are not allowed");
    UserAccount customer =
        users.findById(customerId).orElseThrow(() -> ApiException.notFound("User not found"));
    List<EventSeat> locked = inventory.findAllLocked(ids);
    if (locked.size() != ids.size())
      throw ApiException.notFound("One or more event seats were not found");
    Event event = locked.getFirst().getEvent();
    if (locked.stream().anyMatch(i -> !i.getEvent().getId().equals(event.getId())))
      throw new IllegalArgumentException("All seats must belong to the same event");
    if (event.getStatus() != EventStatus.PUBLISHED || !event.getStartsAt().isAfter(clock.instant()))
      throw ApiException.conflict("Event is not available for reservations");
    Instant now = clock.instant();
    for (EventSeat seat : locked) {
      if (seat.holdExpired(now)) {
        SeatHold stale = seat.getHold();
        seat.release();
        stale.expire();
      }
      if (seat.getStatus() != EventSeatStatus.AVAILABLE)
        throw ApiException.conflict("Seat " + seat.getId() + " is not available");
    }
    SeatHold hold = holds.save(new SeatHold(customer, event, now.plus(duration)));
    locked.forEach(i -> i.placeHold(hold));
    log.info(
        "seat_hold_created holdId={} customerId={} eventId={} seatCount={}",
        hold.getId(),
        customerId,
        event.getId(),
        locked.size());
    return view(hold, locked);
  }

  @Transactional
  public ReservationController.HoldView cancel(UUID customerId, UUID holdId) {
    List<EventSeat> seats = inventory.findByHoldIdLocked(holdId);
    SeatHold hold =
        holds.findLocked(holdId).orElseThrow(() -> ApiException.notFound("Hold not found"));
    assertOwner(customerId, hold);
    if (hold.getStatus() != HoldStatus.ACTIVE)
      throw ApiException.conflict("Only an active hold can be cancelled");
    seats.forEach(EventSeat::release);
    hold.cancel();
    return view(hold, seats);
  }

  @Transactional(readOnly = true)
  public List<ReservationController.HoldView> list(UUID customerId) {
    return holds.findByCustomerIdOrderByCreatedAtDesc(customerId).stream()
        .map(h -> view(h, inventory.findByHoldId(h.getId())))
        .toList();
  }

  @Scheduled(fixedDelayString = "${ticketforge.holds.cleanup-delay-ms:30000}")
  @Transactional
  public void expireStale() {
    Instant now = clock.instant();
    int released = 0;
    for (UUID holdId : inventory.findExpiredHoldIds(now)) {
      List<EventSeat> seats = inventory.findByHoldIdLocked(holdId);
      SeatHold hold = holds.findLocked(holdId).orElse(null);
      if (hold == null || !hold.expired(now)) continue;
      seats.stream().filter(s -> s.heldBy(holdId)).forEach(EventSeat::release);
      released += seats.size();
      hold.expire();
    }
    if (released > 0) log.info("expired_seat_holds releasedSeatCount={}", released);
  }

  private void assertOwner(UUID id, SeatHold h) {
    if (!h.getCustomer().getId().equals(id))
      throw ApiException.forbidden("Hold belongs to another customer");
  }

  private ReservationController.HoldView view(SeatHold h, List<EventSeat> seats) {
    return new ReservationController.HoldView(
        h.getId(),
        h.getEvent().getId(),
        h.getStatus(),
        h.getExpiresAt(),
        seats.stream().map(EventSeat::getId).toList());
  }
}

package com.ticketforge;

import static org.assertj.core.api.Assertions.*;

import com.ticketforge.auth.*;
import com.ticketforge.catalog.*;
import com.ticketforge.common.ApiException;
import com.ticketforge.common.AuditEventRepository;
import com.ticketforge.order.*;
import com.ticketforge.reservation.*;
import com.ticketforge.user.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.testcontainers.junit.jupiter.Testcontainers;

@SpringBootTest
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
@Testcontainers(disabledWithoutDocker = true)
class ReservationConcurrencyIT {
  @Autowired UserRepository users;
  @Autowired VenueRepository venues;
  @Autowired SeatRepository seats;
  @Autowired EventRepository events;
  @Autowired EventSeatRepository inventory;
  @Autowired ReservationService reservations;
  @Autowired SeatHoldRepository holds;
  @Autowired OrderRepository orders;
  @Autowired TicketRepository tickets;
  @Autowired CheckoutService checkout;
  @Autowired CatalogService catalog;
  @Autowired AuthService auth;
  @Autowired RefreshTokenRepository refreshTokens;
  @Autowired ActionTokenRepository actionTokens;
  @Autowired AuditEventRepository audit;

  @BeforeEach
  void clean() {
    audit.deleteAll();
    tickets.deleteAll();
    orders.deleteAll();
    inventory.deleteAll();
    holds.deleteAll();
    events.deleteAll();
    seats.deleteAll();
    venues.deleteAll();
    refreshTokens.deleteAll();
    actionTokens.deleteAll();
    users.deleteAll();
  }

  @Test
  void simultaneousAttemptsForOneSeatProduceExactlyOneHold() throws Exception {
    UserAccount organizer =
        users.save(new UserAccount("org@test.dev", "hash", "Organizer", UserRole.ORGANIZER));
    List<UserAccount> customers = new ArrayList<>();
    for (int i = 0; i < 50; i++)
      customers.add(
          users.save(
              new UserAccount("buyer" + i + "@test.dev", "hash", "Buyer", UserRole.CUSTOMER)));
    Venue venue = venues.save(new Venue("Arena", "1 Main St", "Gainesville", "America/New_York"));
    Seat seat = seats.save(new Seat(venue, "Floor", "A", "1"));
    Event event =
        new Event(
            organizer,
            venue,
            "Concurrency Test",
            "Fifty buyers, one seat",
            Instant.now().plusSeconds(3600));
    event.publish();
    events.save(event);
    EventSeat eventSeat = inventory.save(new EventSeat(event, seat, new BigDecimal("99.00")));
    ExecutorService pool = Executors.newFixedThreadPool(50);
    CountDownLatch ready = new CountDownLatch(50);
    CountDownLatch go = new CountDownLatch(1);
    List<Future<Boolean>> attempts =
        customers.stream()
            .map(u -> pool.submit(poolTask(u.getId(), eventSeat.getId(), ready, go)))
            .toList();
    ready.await(5, TimeUnit.SECONDS);
    go.countDown();
    long winners = 0;
    for (Future<Boolean> attempt : attempts) if (attempt.get(20, TimeUnit.SECONDS)) winners++;
    assertThat(winners).isEqualTo(1);
    assertThat(inventory.findById(eventSeat.getId()).orElseThrow().getStatus())
        .isEqualTo(EventSeatStatus.HELD);
    pool.shutdownNow();
  }

  @Test
  void repeatedIdempotencyKeyReturnsOneOrderAndOneTicket() {
    Fixture f = fixture();
    ReservationController.HoldView hold =
        reservations.create(
            f.first().getId(),
            new ReservationController.CreateHold(List.of(f.inventory().getId())));
    CheckoutController.CheckoutRequest request =
        new CheckoutController.CheckoutRequest(hold.id(), "tok_visa");
    CheckoutController.OrderView first =
        checkout.checkout(f.first().getId(), "checkout-1", request);
    CheckoutController.OrderView retry =
        checkout.checkout(f.first().getId(), "checkout-1", request);
    assertThat(retry.id()).isEqualTo(first.id());
    assertThat(orders.count()).isEqualTo(1);
    assertThat(tickets.count()).isEqualTo(1);
    assertThat(inventory.findById(f.inventory().getId()).orElseThrow().getStatus())
        .isEqualTo(EventSeatStatus.SOLD);
  }

  @Test
  void cleanupReleasesExpiredInventory() {
    Fixture f = fixture();
    SeatHold expired =
        holds.save(new SeatHold(f.first(), f.event(), Instant.now().minusSeconds(1)));
    f.inventory().placeHold(expired);
    inventory.save(f.inventory());
    reservations.expireStale();
    assertThat(inventory.findById(f.inventory().getId()).orElseThrow().getStatus())
        .isEqualTo(EventSeatStatus.AVAILABLE);
    assertThat(holds.findById(expired.getId()).orElseThrow().getStatus())
        .isEqualTo(HoldStatus.EXPIRED);
  }

  @Test
  void cancellationRefundsAndReleasesSeat() {
    Fixture f = fixture();
    var hold =
        reservations.create(
            f.first().getId(),
            new ReservationController.CreateHold(List.of(f.inventory().getId())));
    var order =
        checkout.checkout(
            f.first().getId(),
            "cancel-1",
            new CheckoutController.CheckoutRequest(hold.id(), "tok_visa"));
    var cancelled = checkout.cancel(f.first().getId(), order.id(), "Plans changed");
    assertThat(cancelled.status()).isEqualTo(OrderStatus.REFUNDED);
    assertThat(cancelled.tickets()).allMatch(t -> t.status() == TicketStatus.CANCELLED);
    assertThat(inventory.findById(f.inventory().getId()).orElseThrow().getStatus())
        .isEqualTo(EventSeatStatus.AVAILABLE);
  }

  @Test
  void customerCannotReadAnotherCustomersOrder() {
    Fixture f = fixture();
    var hold =
        reservations.create(
            f.first().getId(),
            new ReservationController.CreateHold(List.of(f.inventory().getId())));
    var order =
        checkout.checkout(
            f.first().getId(),
            "owner-1",
            new CheckoutController.CheckoutRequest(hold.id(), "tok_visa"));
    UserAccount other =
        users.save(new UserAccount("other@test.dev", "hash", "Other", UserRole.CUSTOMER));
    assertThatThrownBy(() -> checkout.get(other.getId(), order.id()))
        .isInstanceOf(ApiException.class)
        .hasMessageContaining("another customer");
  }

  @Test
  void verificationAndRefreshRotationWork() {
    var registration =
        auth.register(
            new AuthController.RegisterRequest(
                "secure@test.dev", "verysecure1", "Secure", UserRole.CUSTOMER));
    auth.verifyEmail(registration.verificationToken());
    var login = auth.login(new AuthController.LoginRequest("secure@test.dev", "verysecure1"));
    var rotated = auth.refresh(login.refreshToken());
    assertThat(rotated.refreshToken()).isNotEqualTo(login.refreshToken());
    assertThatThrownBy(() -> auth.refresh(login.refreshToken())).isInstanceOf(ApiException.class);
  }

  @Test
  void failedPaymentRollsBackCheckoutState() {
    Fixture f = fixture();
    var hold =
        reservations.create(
            f.first().getId(),
            new ReservationController.CreateHold(List.of(f.inventory().getId())));
    assertThatThrownBy(
            () ->
                checkout.checkout(
                    f.first().getId(),
                    "declined-1",
                    new CheckoutController.CheckoutRequest(hold.id(), "tok_decline")))
        .isInstanceOf(ApiException.class);
    assertThat(orders.count()).isZero();
    assertThat(tickets.count()).isZero();
    assertThat(inventory.findById(f.inventory().getId()).orElseThrow().getStatus())
        .isEqualTo(EventSeatStatus.HELD);
  }

  @Test
  void organizerCannotEditAnotherOrganizersEvent() {
    UserAccount owner =
        users.save(new UserAccount("owner@test.dev", "hash", "Owner", UserRole.ORGANIZER));
    UserAccount intruder =
        users.save(new UserAccount("intruder@test.dev", "hash", "Intruder", UserRole.ORGANIZER));
    Venue venue = venues.save(new Venue("Hall", "1 Road", "City", "UTC"));
    Event event =
        events.save(
            new Event(owner, venue, "Private Draft", "Draft", Instant.now().plusSeconds(86400)));
    assertThatThrownBy(
            () ->
                catalog.update(
                    intruder.getId(),
                    event.getId(),
                    new CatalogController.UpdateEvent(
                        "Hijacked", "No", Instant.now().plusSeconds(90000), "MUSIC")))
        .isInstanceOf(ApiException.class)
        .hasMessageContaining("do not manage");
  }

  private Callable<Boolean> poolTask(
      UUID user, UUID seat, CountDownLatch ready, CountDownLatch go) {
    return () -> {
      ready.countDown();
      go.await();
      try {
        reservations.create(user, new ReservationController.CreateHold(List.of(seat)));
        return true;
      } catch (ApiException ex) {
        return false;
      }
    };
  }

  private Callable<Boolean> attempt(Callable<Boolean> c) {
    return c;
  }

  private Fixture fixture() {
    UserAccount organizer =
        users.save(
            new UserAccount(
                "org-" + UUID.randomUUID() + "@test.dev", "hash", "Organizer", UserRole.ORGANIZER));
    UserAccount customer =
        users.save(
            new UserAccount(
                "user-" + UUID.randomUUID() + "@test.dev", "hash", "Customer", UserRole.CUSTOMER));
    Venue venue = venues.save(new Venue("Arena", "1 Main St", "Gainesville", "America/New_York"));
    Seat seat = seats.save(new Seat(venue, "Floor", "A", "1"));
    Event event =
        new Event(organizer, venue, "Test Event", "Test", Instant.now().plusSeconds(604800));
    event.publish();
    events.save(event);
    return new Fixture(
        customer, event, inventory.save(new EventSeat(event, seat, new BigDecimal("50.00"))));
  }

  private record Fixture(UserAccount first, Event event, EventSeat inventory) {}
}

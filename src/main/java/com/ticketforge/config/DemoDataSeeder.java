package com.ticketforge.config;

import com.ticketforge.catalog.Event;
import com.ticketforge.catalog.EventRepository;
import com.ticketforge.catalog.EventSeat;
import com.ticketforge.catalog.EventSeatRepository;
import com.ticketforge.catalog.Seat;
import com.ticketforge.catalog.SeatRepository;
import com.ticketforge.catalog.Venue;
import com.ticketforge.catalog.VenueRepository;
import com.ticketforge.user.UserAccount;
import com.ticketforge.user.UserRepository;
import com.ticketforge.user.UserRole;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@ConditionalOnProperty(name = "ticketforge.demo.enabled", havingValue = "true")
public class DemoDataSeeder implements ApplicationRunner {
  private static final Logger log = LoggerFactory.getLogger(DemoDataSeeder.class);
  private final UserRepository users;
  private final VenueRepository venues;
  private final SeatRepository seats;
  private final EventRepository events;
  private final EventSeatRepository inventory;
  private final PasswordEncoder passwords;
  private final String demoPassword;

  public DemoDataSeeder(
      UserRepository users,
      VenueRepository venues,
      SeatRepository seats,
      EventRepository events,
      EventSeatRepository inventory,
      PasswordEncoder passwords,
      @Value("${ticketforge.demo.password:DemoPass123!}") String demoPassword) {
    this.users = users;
    this.venues = venues;
    this.seats = seats;
    this.events = events;
    this.inventory = inventory;
    this.passwords = passwords;
    this.demoPassword = demoPassword;
  }

  @Override
  @Transactional
  public void run(ApplicationArguments args) {
    if (users.existsByEmailIgnoreCase("customer@ticketforge.local")) {
      log.info("Demo data already exists; seeding skipped");
      return;
    }

    UserAccount admin = account("admin@ticketforge.local", "Demo Admin", UserRole.ADMIN);
    UserAccount organizer =
        account("organizer@ticketforge.local", "River City Events", UserRole.ORGANIZER);
    organizer.approveOrganizer();
    account("customer@ticketforge.local", "Alex Rivera", UserRole.CUSTOMER);

    Venue venue =
        venues.save(
            new Venue("Riverfront Hall", "100 Foundry Avenue", "Pittsburgh", "America/New_York"));
    List<Seat> venueSeats = new ArrayList<>();
    for (String section : List.of("FLOOR", "BALCONY")) {
      for (int row = 1; row <= 3; row++) {
        for (int number = 1; number <= 5; number++) {
          venueSeats.add(
              seats.save(new Seat(venue, section, String.valueOf(row), String.valueOf(number))));
        }
      }
    }

    seedEvent(
        organizer,
        venue,
        venueSeats,
        "Neon Skyline Live",
        "An immersive synth-pop show with a full visual production.",
        "MUSIC",
        21,
        new BigDecimal("74.00"));
    seedEvent(
        organizer,
        venue,
        venueSeats,
        "Steel City Tech Summit",
        "A one-day gathering for builders, founders, and backend engineers.",
        "CONFERENCE",
        35,
        new BigDecimal("129.00"));
    seedEvent(
        organizer,
        venue,
        venueSeats,
        "Midnight Comedy Club",
        "A rotating lineup of touring and local stand-up performers.",
        "COMEDY",
        49,
        new BigDecimal("42.00"));

    log.info(
        "Demo data seeded admin={} organizer={} customer={} passwordConfigured={}",
        admin.getEmail(),
        organizer.getEmail(),
        "customer@ticketforge.local",
        !demoPassword.isBlank());
  }

  private UserAccount account(String email, String name, UserRole role) {
    UserAccount account = new UserAccount(email, passwords.encode(demoPassword), name, role);
    account.verifyEmail();
    return users.save(account);
  }

  private void seedEvent(
      UserAccount organizer,
      Venue venue,
      List<Seat> venueSeats,
      String title,
      String description,
      String category,
      int daysFromNow,
      BigDecimal basePrice) {
    Event event =
        events.save(
            new Event(
                organizer,
                venue,
                title,
                description,
                Instant.now().plus(daysFromNow, ChronoUnit.DAYS),
                category));
    event.publish();
    inventory.saveAll(
        venueSeats.stream()
            .map(
                seat ->
                    new EventSeat(
                        event,
                        seat,
                        "BALCONY".equals(seat.getSectionName())
                            ? basePrice.subtract(new BigDecimal("15.00"))
                            : basePrice))
            .toList());
  }
}

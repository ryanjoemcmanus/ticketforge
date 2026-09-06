package com.ticketforge.catalog;

import com.ticketforge.common.*;
import com.ticketforge.user.*;
import java.time.Instant;
import java.util.*;
import org.springframework.data.domain.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CatalogService {
  private final VenueRepository venues;
  private final SeatRepository seats;
  private final EventRepository events;
  private final EventSeatRepository inventory;
  private final UserRepository users;

  CatalogService(
      VenueRepository venues,
      SeatRepository seats,
      EventRepository events,
      EventSeatRepository inventory,
      UserRepository users) {
    this.venues = venues;
    this.seats = seats;
    this.events = events;
    this.inventory = inventory;
    this.users = users;
  }

  @Transactional
  public CatalogController.VenueView createVenue(CatalogController.CreateVenue r) {
    Venue v = venues.save(new Venue(r.name(), r.address(), r.city(), r.timezone()));
    return venueView(v);
  }

  @Transactional(readOnly = true)
  public List<CatalogController.VenueView> venues() {
    return venues.findAll(Sort.by("name")).stream().map(this::venueView).toList();
  }

  @Transactional
  public List<CatalogController.SeatView> addSeats(
      UUID venueId, List<CatalogController.CreateSeat> requests) {
    Venue v = venues.findById(venueId).orElseThrow(() -> ApiException.notFound("Venue not found"));
    return requests.stream()
        .map(r -> seatView(seats.save(new Seat(v, r.section(), r.row(), r.number()))))
        .toList();
  }

  @Transactional
  public CatalogController.EventView createEvent(UUID actorId, CatalogController.CreateEvent r) {
    UserAccount actor =
        users.findById(actorId).orElseThrow(() -> ApiException.notFound("User not found"));
    Venue venue =
        venues.findById(r.venueId()).orElseThrow(() -> ApiException.notFound("Venue not found"));
    List<Seat> venueSeats = seats.findByVenueId(venue.getId());
    if (venueSeats.isEmpty())
      throw ApiException.conflict("Add venue seats before creating an event");
    if (actor.getRole() == UserRole.ORGANIZER && !actor.isOrganizerApproved())
      throw ApiException.forbidden("Organizer approval required");
    Event event =
        events.save(
            new Event(actor, venue, r.title(), r.description(), r.startsAt(), r.category()));
    inventory.saveAll(
        venueSeats.stream().map(s -> new EventSeat(event, s, r.seatPrice())).toList());
    return eventView(event);
  }

  @Transactional
  public CatalogController.EventView publish(UUID actorId, UUID eventId) {
    Event e = managed(actorId, eventId);
    if (!e.getStartsAt().isAfter(Instant.now()))
      throw ApiException.conflict("A past event cannot be published");
    e.publish();
    return eventView(e);
  }

  @Transactional(readOnly = true)
  public CatalogController.EventPage browse(int page, int size) {
    Page<Event> p =
        events.findByStatusAndStartsAtAfter(
            EventStatus.PUBLISHED,
            Instant.now(),
            PageRequest.of(page, Math.min(size, 100), Sort.by("startsAt")));
    return new CatalogController.EventPage(
        p.getContent().stream().map(this::eventView).toList(),
        p.getNumber(),
        p.getSize(),
        p.getTotalElements(),
        p.getTotalPages());
  }

  @Transactional(readOnly = true)
  public CatalogController.EventPage search(String q, String category, int page, int size) {
    Page<Event> p =
        events.search(
            Instant.now(),
            category == null ? null : category.toUpperCase(),
            q,
            PageRequest.of(page, Math.min(size, 100), Sort.by("startsAt")));
    return new CatalogController.EventPage(
        p.getContent().stream().map(this::eventView).toList(),
        p.getNumber(),
        p.getSize(),
        p.getTotalElements(),
        p.getTotalPages());
  }

  @Transactional
  public CatalogController.EventView update(
      UUID actorId, UUID eventId, CatalogController.UpdateEvent r) {
    Event e = managed(actorId, eventId);
    if (e.getStatus() != EventStatus.DRAFT)
      throw ApiException.conflict("Only draft events can be edited");
    e.update(r.title(), r.description(), r.startsAt(), r.category());
    return eventView(e);
  }

  @Transactional
  public CatalogController.EventView cancel(UUID actorId, UUID eventId) {
    Event e = managed(actorId, eventId);
    boolean sold =
        inventory
            .findByEventIdOrderBySeatSectionNameAscSeatRowLabelAscSeatSeatNumberAsc(eventId)
            .stream()
            .anyMatch(i -> i.getStatus() == EventSeatStatus.SOLD);
    if (sold) throw ApiException.conflict("Refund sold orders before cancelling this event");
    e.cancel();
    return eventView(e);
  }

  @Transactional
  public List<CatalogController.InventoryView> priceSection(
      UUID actorId, UUID eventId, CatalogController.SectionPrice r) {
    Event e = managed(actorId, eventId);
    if (e.getStatus() != EventStatus.DRAFT)
      throw ApiException.conflict("Pricing can change only while an event is draft");
    List<EventSeat> all =
        inventory.findByEventIdOrderBySeatSectionNameAscSeatRowLabelAscSeatSeatNumberAsc(eventId);
    all.stream()
        .filter(i -> i.getSeat().getSectionName().equalsIgnoreCase(r.section()))
        .forEach(i -> i.reprice(r.price()));
    return all.stream().map(this::inventoryView).toList();
  }

  @Transactional(readOnly = true)
  public List<CatalogController.EventAnalytics> analytics(UUID actorId) {
    return events.findByOrganizerIdOrderByStartsAtDesc(actorId).stream()
        .map(
            e -> {
              List<EventSeat> inv =
                  inventory.findByEventIdOrderBySeatSectionNameAscSeatRowLabelAscSeatSeatNumberAsc(
                      e.getId());
              long sold = inv.stream().filter(i -> i.getStatus() == EventSeatStatus.SOLD).count();
              long held = inv.stream().filter(i -> i.getStatus() == EventSeatStatus.HELD).count();
              java.math.BigDecimal revenue =
                  inv.stream()
                      .filter(i -> i.getStatus() == EventSeatStatus.SOLD)
                      .map(EventSeat::getPrice)
                      .reduce(java.math.BigDecimal.ZERO, java.math.BigDecimal::add);
              return new CatalogController.EventAnalytics(
                  e.getId(), e.getTitle(), inv.size(), sold, held, revenue);
            })
        .toList();
  }

  @Transactional(readOnly = true)
  public CatalogController.EventDetails details(UUID eventId) {
    Event e = events.findById(eventId).orElseThrow(() -> ApiException.notFound("Event not found"));
    List<CatalogController.InventoryView> items =
        inventory
            .findByEventIdOrderBySeatSectionNameAscSeatRowLabelAscSeatSeatNumberAsc(eventId)
            .stream()
            .map(this::inventoryView)
            .toList();
    return new CatalogController.EventDetails(eventView(e), items);
  }

  private Event managed(UUID actorId, UUID eventId) {
    Event e = events.findById(eventId).orElseThrow(() -> ApiException.notFound("Event not found"));
    UserAccount actor =
        users.findById(actorId).orElseThrow(() -> ApiException.notFound("User not found"));
    if (actor.getRole() != UserRole.ADMIN && !e.getOrganizer().getId().equals(actorId))
      throw ApiException.forbidden("You do not manage this event");
    return e;
  }

  private CatalogController.VenueView venueView(Venue v) {
    return new CatalogController.VenueView(
        v.getId(), v.getName(), v.getAddress(), v.getCity(), v.getTimezone());
  }

  private CatalogController.SeatView seatView(Seat s) {
    return new CatalogController.SeatView(
        s.getId(), s.getSectionName(), s.getRowLabel(), s.getSeatNumber());
  }

  private CatalogController.EventView eventView(Event e) {
    return new CatalogController.EventView(
        e.getId(),
        e.getTitle(),
        e.getDescription(),
        e.getCategory(),
        e.getStartsAt(),
        e.getStatus(),
        venueView(e.getVenue()),
        e.getOrganizer().getId());
  }

  private CatalogController.InventoryView inventoryView(EventSeat i) {
    return new CatalogController.InventoryView(
        i.getId(), seatView(i.getSeat()), i.getPrice(), i.getStatus(), i.getHoldExpiresAt());
  }
}

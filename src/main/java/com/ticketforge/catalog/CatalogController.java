package com.ticketforge.catalog;

import com.ticketforge.common.CurrentUser;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;
import org.springframework.http.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1")
public class CatalogController {
  private final CatalogService service;

  CatalogController(CatalogService service) {
    this.service = service;
  }

  @PostMapping("/venues")
  @PreAuthorize("hasAnyRole('ORGANIZER','ADMIN')")
  ResponseEntity<VenueView> createVenue(@Valid @RequestBody CreateVenue r) {
    return ResponseEntity.status(HttpStatus.CREATED).body(service.createVenue(r));
  }

  @GetMapping("/venues")
  @PreAuthorize("hasAnyRole('ORGANIZER','ADMIN')")
  List<VenueView> venues() {
    return service.venues();
  }

  @PostMapping("/venues/{id}/seats")
  @PreAuthorize("hasAnyRole('ORGANIZER','ADMIN')")
  ResponseEntity<List<SeatView>> addSeats(
      @PathVariable UUID id, @RequestBody @Size(min = 1, max = 1000) List<@Valid CreateSeat> r) {
    return ResponseEntity.status(HttpStatus.CREATED).body(service.addSeats(id, r));
  }

  @PostMapping("/events")
  @PreAuthorize("hasAnyRole('ORGANIZER','ADMIN')")
  ResponseEntity<EventView> createEvent(Authentication auth, @Valid @RequestBody CreateEvent r) {
    return ResponseEntity.status(HttpStatus.CREATED)
        .body(service.createEvent(CurrentUser.id(auth), r));
  }

  @PostMapping("/events/{id}/publish")
  @PreAuthorize("hasAnyRole('ORGANIZER','ADMIN')")
  EventView publish(Authentication auth, @PathVariable UUID id) {
    return service.publish(CurrentUser.id(auth), id);
  }

  @PutMapping("/events/{id}")
  @PreAuthorize("hasAnyRole('ORGANIZER','ADMIN')")
  EventView update(Authentication auth, @PathVariable UUID id, @Valid @RequestBody UpdateEvent r) {
    return service.update(CurrentUser.id(auth), id, r);
  }

  @PostMapping("/events/{id}/cancel")
  @PreAuthorize("hasAnyRole('ORGANIZER','ADMIN')")
  EventView cancel(Authentication auth, @PathVariable UUID id) {
    return service.cancel(CurrentUser.id(auth), id);
  }

  @PutMapping("/events/{id}/section-prices")
  @PreAuthorize("hasAnyRole('ORGANIZER','ADMIN')")
  List<InventoryView> price(
      Authentication auth, @PathVariable UUID id, @Valid @RequestBody SectionPrice r) {
    return service.priceSection(CurrentUser.id(auth), id, r);
  }

  @GetMapping("/organizer/analytics")
  @PreAuthorize("hasRole('ORGANIZER')")
  List<EventAnalytics> analytics(Authentication auth) {
    return service.analytics(CurrentUser.id(auth));
  }

  @GetMapping("/events")
  EventPage browse(
      @RequestParam(required = false) String q,
      @RequestParam(required = false) String category,
      @RequestParam(defaultValue = "0") @Min(0) int page,
      @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
    return q == null && category == null
        ? service.browse(page, size)
        : service.search(q, category, page, size);
  }

  @GetMapping("/events/{id}")
  EventDetails details(@PathVariable UUID id) {
    return service.details(id);
  }

  public record CreateVenue(
      @NotBlank @Size(max = 160) String name,
      @NotBlank @Size(max = 240) String address,
      @NotBlank @Size(max = 120) String city,
      @NotBlank @Size(max = 80) String timezone) {}

  public record CreateSeat(
      @NotBlank @Size(max = 80) String section,
      @NotBlank @Size(max = 40) String row,
      @NotBlank @Size(max = 40) String number) {}

  public record CreateEvent(
      @NotNull UUID venueId,
      @NotBlank @Size(max = 180) String title,
      @Size(max = 2000) String description,
      @NotNull @Future Instant startsAt,
      @NotBlank @Size(max = 80) String category,
      @NotNull @DecimalMin("0.00") @Digits(integer = 10, fraction = 2) BigDecimal seatPrice) {}

  public record UpdateEvent(
      @NotBlank @Size(max = 180) String title,
      @Size(max = 2000) String description,
      @NotNull @Future Instant startsAt,
      @NotBlank @Size(max = 80) String category) {}

  public record SectionPrice(
      @NotBlank String section,
      @NotNull @DecimalMin("0.00") @Digits(integer = 10, fraction = 2) BigDecimal price) {}

  public record VenueView(UUID id, String name, String address, String city, String timezone) {}

  public record SeatView(UUID id, String section, String row, String number) {}

  public record EventView(
      UUID id,
      String title,
      String description,
      String category,
      Instant startsAt,
      EventStatus status,
      VenueView venue,
      UUID organizerId) {}

  public record InventoryView(
      UUID id, SeatView seat, BigDecimal price, EventSeatStatus status, Instant holdExpiresAt) {}

  public record EventDetails(EventView event, List<InventoryView> inventory) {}

  public record EventPage(
      List<EventView> content, int page, int size, long totalElements, int totalPages) {}

  public record EventAnalytics(
      UUID eventId, String title, int capacity, long sold, long held, BigDecimal revenue) {}
}

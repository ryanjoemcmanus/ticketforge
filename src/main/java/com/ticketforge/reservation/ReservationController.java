package com.ticketforge.reservation;

import com.ticketforge.common.CurrentUser;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.time.Instant;
import java.util.*;
import org.springframework.http.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/holds")
@PreAuthorize("hasRole('CUSTOMER')")
public class ReservationController {
  private final ReservationService service;

  ReservationController(ReservationService service) {
    this.service = service;
  }

  @PostMapping
  ResponseEntity<HoldView> create(Authentication auth, @Valid @RequestBody CreateHold r) {
    return ResponseEntity.status(HttpStatus.CREATED).body(service.create(CurrentUser.id(auth), r));
  }

  @DeleteMapping("/{id}")
  HoldView cancel(Authentication auth, @PathVariable UUID id) {
    return service.cancel(CurrentUser.id(auth), id);
  }

  @GetMapping
  List<HoldView> list(Authentication auth) {
    return service.list(CurrentUser.id(auth));
  }

  public record CreateHold(@NotEmpty @Size(max = 8) List<@NotNull UUID> eventSeatIds) {}

  public record HoldView(
      UUID id, UUID eventId, HoldStatus status, Instant expiresAt, List<UUID> eventSeatIds) {}
}

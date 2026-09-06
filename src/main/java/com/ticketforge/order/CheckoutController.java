package com.ticketforge.order;

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
@RequestMapping("/api/v1/orders")
@PreAuthorize("hasRole('CUSTOMER')")
public class CheckoutController {
  private final CheckoutService service;

  CheckoutController(CheckoutService service) {
    this.service = service;
  }

  @PostMapping
  ResponseEntity<OrderView> checkout(
      Authentication auth,
      @RequestHeader("Idempotency-Key") String key,
      @Valid @RequestBody CheckoutRequest r) {
    return ResponseEntity.status(HttpStatus.CREATED)
        .body(service.checkout(CurrentUser.id(auth), key, r));
  }

  @GetMapping
  List<OrderView> list(Authentication auth) {
    return service.list(CurrentUser.id(auth));
  }

  @GetMapping("/{id}")
  OrderView get(Authentication auth, @PathVariable UUID id) {
    return service.get(CurrentUser.id(auth), id);
  }

  @PostMapping("/{id}/cancel")
  OrderView cancel(
      Authentication auth, @PathVariable UUID id, @Valid @RequestBody CancelRequest r) {
    return service.cancel(CurrentUser.id(auth), id, r.reason());
  }

  public record CheckoutRequest(@NotNull UUID holdId, @NotBlank String paymentToken) {}

  public record CancelRequest(@NotBlank @Size(max = 500) String reason) {}

  public record TicketView(
      UUID id,
      String ticketCode,
      TicketStatus status,
      UUID eventId,
      UUID eventSeatId,
      String section,
      String row,
      String seat) {}

  public record OrderView(
      UUID id,
      OrderStatus status,
      BigDecimal totalAmount,
      String paymentReference,
      Instant createdAt,
      List<TicketView> tickets) {}
}

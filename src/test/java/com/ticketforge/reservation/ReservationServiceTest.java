package com.ticketforge.reservation;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.ticketforge.catalog.EventSeatRepository;
import com.ticketforge.user.UserRepository;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.Test;

class ReservationServiceTest {
  @Test
  void rejectsDuplicateInventoryIdsBeforeLocking() {
    EventSeatRepository inventory = mock(EventSeatRepository.class);
    ReservationService service =
        new ReservationService(
            inventory,
            mock(SeatHoldRepository.class),
            mock(UserRepository.class),
            Duration.ofMinutes(10),
            Clock.systemUTC());
    UUID id = UUID.randomUUID();
    assertThatThrownBy(
            () ->
                service.create(
                    UUID.randomUUID(), new ReservationController.CreateHold(List.of(id, id))))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("Duplicate");
    verifyNoInteractions(inventory);
  }
}

package com.ticketforge.catalog;

import java.util.*;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SeatRepository extends JpaRepository<Seat, UUID> {
  List<Seat> findByVenueId(UUID venueId);
}

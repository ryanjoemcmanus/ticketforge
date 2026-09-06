package com.ticketforge.order;

import java.util.*;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;

public interface TicketRepository extends JpaRepository<Ticket, UUID> {
  @Query(
      "select t from Ticket t join fetch t.eventSeat es join fetch es.seat join fetch es.event where t.order.id=:orderId")
  List<Ticket> findDetailedByOrderId(@Param("orderId") UUID orderId);
}

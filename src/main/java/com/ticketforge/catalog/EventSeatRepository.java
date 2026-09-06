package com.ticketforge.catalog;

import jakarta.persistence.LockModeType;
import java.time.Instant;
import java.util.*;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;

public interface EventSeatRepository extends JpaRepository<EventSeat, UUID> {
  List<EventSeat> findByEventIdOrderBySeatSectionNameAscSeatRowLabelAscSeatSeatNumberAsc(
      UUID eventId);

  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query(
      "select es from EventSeat es join fetch es.event join fetch es.seat where es.id in :ids order by es.id")
  List<EventSeat> findAllLocked(@Param("ids") Collection<UUID> ids);

  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("select es from EventSeat es where es.hold.id=:holdId order by es.id")
  List<EventSeat> findByHoldIdLocked(@Param("holdId") UUID holdId);

  @Query("select es from EventSeat es join fetch es.seat where es.hold.id=:holdId order by es.id")
  List<EventSeat> findByHoldId(@Param("holdId") UUID holdId);

  @Query(
      "select distinct es.hold.id from EventSeat es where es.status=com.ticketforge.catalog.EventSeatStatus.HELD and es.holdExpiresAt <= :now")
  List<UUID> findExpiredHoldIds(@Param("now") Instant now);
}

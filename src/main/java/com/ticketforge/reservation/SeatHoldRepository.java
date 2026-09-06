package com.ticketforge.reservation;

import jakarta.persistence.LockModeType;
import java.util.*;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;

public interface SeatHoldRepository extends JpaRepository<SeatHold, UUID> {
  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("select h from SeatHold h where h.id=:id")
  Optional<SeatHold> findLocked(@Param("id") UUID id);

  List<SeatHold> findByCustomerIdOrderByCreatedAtDesc(UUID customerId);
}

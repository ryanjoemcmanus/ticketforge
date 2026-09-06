package com.ticketforge.order;

import java.util.*;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OrderRepository extends JpaRepository<CustomerOrder, UUID> {
  @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
  @org.springframework.data.jpa.repository.Query("select o from CustomerOrder o where o.id=:id")
  Optional<CustomerOrder> findLocked(
      @org.springframework.data.repository.query.Param("id") UUID id);

  Optional<CustomerOrder> findByCustomerIdAndIdempotencyKey(UUID customerId, String key);

  Optional<CustomerOrder> findByPaymentReference(String paymentReference);

  List<CustomerOrder> findByCustomerIdOrderByCreatedAtDesc(UUID customerId);
}

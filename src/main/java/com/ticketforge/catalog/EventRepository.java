package com.ticketforge.catalog;

import java.time.Instant;
import java.util.*;
import org.springframework.data.domain.*;
import org.springframework.data.jpa.repository.JpaRepository;

public interface EventRepository extends JpaRepository<Event, UUID> {
  Page<Event> findByStatusAndStartsAtAfter(EventStatus status, Instant after, Pageable pageable);

  @org.springframework.data.jpa.repository.Query(
      "select e from Event e where e.status=com.ticketforge.catalog.EventStatus.PUBLISHED and e.startsAt>:now and (:category is null or e.category=:category) and (:q is null or lower(e.title) like lower(concat('%',:q,'%')) or lower(e.description) like lower(concat('%',:q,'%')))")
  Page<Event> search(
      @org.springframework.data.repository.query.Param("now") Instant now,
      @org.springframework.data.repository.query.Param("category") String category,
      @org.springframework.data.repository.query.Param("q") String q,
      Pageable pageable);

  List<Event> findByOrganizerIdOrderByStartsAtDesc(UUID organizerId);
}

package com.ticketforge.catalog;

import com.ticketforge.user.UserAccount;
import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "events")
public class Event {
  @Id private UUID id;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "organizer_id")
  private UserAccount organizer;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "venue_id")
  private Venue venue;

  @Column(nullable = false, length = 180)
  private String title;

  @Column(length = 2000)
  private String description;

  @Column(nullable = false, length = 80)
  private String category;

  @Column(name = "starts_at", nullable = false)
  private Instant startsAt;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 20)
  private EventStatus status;

  @Column(name = "created_at", nullable = false)
  private Instant createdAt;

  @Version private long version;

  protected Event() {}

  public Event(
      UserAccount organizer, Venue venue, String title, String description, Instant startsAt) {
    this(organizer, venue, title, description, startsAt, "GENERAL");
  }

  public Event(
      UserAccount organizer,
      Venue venue,
      String title,
      String description,
      Instant startsAt,
      String category) {
    this.id = UUID.randomUUID();
    this.organizer = organizer;
    this.venue = venue;
    this.title = title;
    this.description = description;
    this.category = category.toUpperCase();
    this.startsAt = startsAt;
    this.status = EventStatus.DRAFT;
    this.createdAt = Instant.now();
  }

  public void publish() {
    this.status = EventStatus.PUBLISHED;
  }

  public void update(String title, String description, Instant startsAt) {
    this.title = title;
    this.description = description;
    this.startsAt = startsAt;
  }

  public void update(String title, String description, Instant startsAt, String category) {
    update(title, description, startsAt);
    this.category = category.toUpperCase();
  }

  public void cancel() {
    if (status == EventStatus.COMPLETED)
      throw new IllegalStateException("Completed event cannot be cancelled");
    status = EventStatus.CANCELLED;
  }

  public UUID getId() {
    return id;
  }

  public UserAccount getOrganizer() {
    return organizer;
  }

  public Venue getVenue() {
    return venue;
  }

  public String getTitle() {
    return title;
  }

  public String getDescription() {
    return description;
  }

  public Instant getStartsAt() {
    return startsAt;
  }

  public EventStatus getStatus() {
    return status;
  }

  public String getCategory() {
    return category;
  }
}

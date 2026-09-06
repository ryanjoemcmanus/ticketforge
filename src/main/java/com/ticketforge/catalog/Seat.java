package com.ticketforge.catalog;

import jakarta.persistence.*;
import java.util.UUID;

@Entity
@Table(
    name = "seats",
    uniqueConstraints =
        @UniqueConstraint(columnNames = {"venue_id", "section_name", "row_label", "seat_number"}))
public class Seat {
  @Id private UUID id;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "venue_id")
  private Venue venue;

  @Column(name = "section_name", nullable = false, length = 80)
  private String sectionName;

  @Column(name = "row_label", nullable = false, length = 40)
  private String rowLabel;

  @Column(name = "seat_number", nullable = false, length = 40)
  private String seatNumber;

  protected Seat() {}

  public Seat(Venue venue, String sectionName, String rowLabel, String seatNumber) {
    this.id = UUID.randomUUID();
    this.venue = venue;
    this.sectionName = sectionName;
    this.rowLabel = rowLabel;
    this.seatNumber = seatNumber;
  }

  public UUID getId() {
    return id;
  }

  public Venue getVenue() {
    return venue;
  }

  public String getSectionName() {
    return sectionName;
  }

  public String getRowLabel() {
    return rowLabel;
  }

  public String getSeatNumber() {
    return seatNumber;
  }
}

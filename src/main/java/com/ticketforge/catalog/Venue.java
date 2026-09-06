package com.ticketforge.catalog;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "venues")
public class Venue {
  @Id private UUID id;

  @Column(nullable = false, length = 160)
  private String name;

  @Column(nullable = false, length = 240)
  private String address;

  @Column(nullable = false, length = 120)
  private String city;

  @Column(nullable = false, length = 80)
  private String timezone;

  @Column(name = "created_at", nullable = false)
  private Instant createdAt;

  protected Venue() {}

  public Venue(String name, String address, String city, String timezone) {
    this.id = UUID.randomUUID();
    this.name = name;
    this.address = address;
    this.city = city;
    this.timezone = timezone;
    this.createdAt = Instant.now();
  }

  public UUID getId() {
    return id;
  }

  public String getName() {
    return name;
  }

  public String getAddress() {
    return address;
  }

  public String getCity() {
    return city;
  }

  public String getTimezone() {
    return timezone;
  }
}

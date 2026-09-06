package com.ticketforge.common;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "audit_events")
public class AuditEvent {
  @Id private UUID id;

  @Column(name = "actor_id")
  private UUID actorId;

  @Column(nullable = false, length = 80)
  private String action;

  @Column(name = "entity_type", nullable = false, length = 80)
  private String entityType;

  @Column(name = "entity_id", nullable = false)
  private UUID entityId;

  @Column(length = 1000)
  private String details;

  @Column(name = "created_at", nullable = false)
  private Instant createdAt;

  protected AuditEvent() {}

  public AuditEvent(UUID actor, String action, String type, UUID entity, String details) {
    id = UUID.randomUUID();
    actorId = actor;
    this.action = action;
    entityType = type;
    entityId = entity;
    this.details = details;
    createdAt = Instant.now();
  }
}

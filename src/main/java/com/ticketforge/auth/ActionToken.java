package com.ticketforge.auth;

import com.ticketforge.user.UserAccount;
import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "account_action_tokens")
public class ActionToken {
  public enum Type {
    EMAIL_VERIFICATION,
    PASSWORD_RESET
  }

  @Id private UUID id;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "user_id")
  private UserAccount user;

  @Column(name = "token_hash", nullable = false, unique = true, length = 64)
  private String tokenHash;

  @Enumerated(EnumType.STRING)
  @Column(name = "token_type", nullable = false, length = 30)
  private Type type;

  @Column(name = "expires_at", nullable = false)
  private Instant expiresAt;

  @Column(name = "used_at")
  private Instant usedAt;

  @Column(name = "created_at", nullable = false)
  private Instant createdAt;

  protected ActionToken() {}

  public ActionToken(UserAccount user, String hash, Type type, Instant expires) {
    id = UUID.randomUUID();
    this.user = user;
    tokenHash = hash;
    this.type = type;
    expiresAt = expires;
    createdAt = Instant.now();
  }

  public boolean usable(Instant now) {
    return usedAt == null && expiresAt.isAfter(now);
  }

  public void use() {
    usedAt = Instant.now();
  }

  public UserAccount getUser() {
    return user;
  }

  public Type getType() {
    return type;
  }
}

package com.ticketforge.auth;

import com.ticketforge.user.UserAccount;
import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "refresh_tokens")
public class RefreshToken {
  @Id private UUID id;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "user_id")
  private UserAccount user;

  @Column(name = "token_hash", nullable = false, unique = true, length = 64)
  private String tokenHash;

  @Column(name = "expires_at", nullable = false)
  private Instant expiresAt;

  @Column(name = "revoked_at")
  private Instant revokedAt;

  @Column(name = "created_at", nullable = false)
  private Instant createdAt;

  protected RefreshToken() {}

  public RefreshToken(UserAccount user, String hash, Instant expires) {
    id = UUID.randomUUID();
    this.user = user;
    tokenHash = hash;
    expiresAt = expires;
    createdAt = Instant.now();
  }

  public boolean active(Instant now) {
    return revokedAt == null && expiresAt.isAfter(now);
  }

  public void revoke() {
    if (revokedAt == null) revokedAt = Instant.now();
  }

  public UserAccount getUser() {
    return user;
  }
}

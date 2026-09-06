package com.ticketforge.user;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "app_users")
public class UserAccount {
  @Id private UUID id;

  @Column(nullable = false, unique = true, length = 320)
  private String email;

  @Column(name = "password_hash", nullable = false, length = 100)
  private String passwordHash;

  @Column(name = "display_name", nullable = false, length = 120)
  private String displayName;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 20)
  private UserRole role;

  @Column(name = "created_at", nullable = false)
  private Instant createdAt;

  @Column(name = "email_verified", nullable = false)
  private boolean emailVerified;

  @Column(name = "organizer_approved", nullable = false)
  private boolean organizerApproved;

  @Column(name = "failed_login_attempts", nullable = false)
  private int failedLoginAttempts;

  @Column(name = "locked_until")
  private Instant lockedUntil;

  @Column(name = "token_version", nullable = false)
  private int tokenVersion;

  protected UserAccount() {}

  public UserAccount(String email, String passwordHash, String displayName, UserRole role) {
    this.id = UUID.randomUUID();
    this.email = email.toLowerCase();
    this.passwordHash = passwordHash;
    this.displayName = displayName;
    this.role = role;
    this.createdAt = Instant.now();
    this.emailVerified = false;
    this.organizerApproved = role != UserRole.ORGANIZER;
  }

  public boolean locked(Instant now) {
    return lockedUntil != null && lockedUntil.isAfter(now);
  }

  public void recordFailedLogin(Instant now) {
    failedLoginAttempts++;
    if (failedLoginAttempts >= 5) lockedUntil = now.plusSeconds(900);
  }

  public void recordSuccessfulLogin() {
    failedLoginAttempts = 0;
    lockedUntil = null;
  }

  public void verifyEmail() {
    emailVerified = true;
  }

  public void approveOrganizer() {
    if (role == UserRole.ORGANIZER) organizerApproved = true;
  }

  public void changePassword(String hash) {
    passwordHash = hash;
    tokenVersion++;
  }

  public UUID getId() {
    return id;
  }

  public String getEmail() {
    return email;
  }

  public String getPasswordHash() {
    return passwordHash;
  }

  public String getDisplayName() {
    return displayName;
  }

  public UserRole getRole() {
    return role;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }

  public boolean isEmailVerified() {
    return emailVerified;
  }

  public boolean isOrganizerApproved() {
    return organizerApproved;
  }

  public int getFailedLoginAttempts() {
    return failedLoginAttempts;
  }

  public Instant getLockedUntil() {
    return lockedUntil;
  }

  public int getTokenVersion() {
    return tokenVersion;
  }
}

package com.ticketforge.auth;

import com.ticketforge.common.*;
import com.ticketforge.notification.NotificationEvents;
import com.ticketforge.user.*;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.time.*;
import java.util.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthService {
  private final UserRepository users;
  private final PasswordEncoder passwords;
  private final JwtEncoder encoder;
  private final RefreshTokenRepository refreshTokens;
  private final ActionTokenRepository actionTokens;
  private final Duration ttl;
  private final String issuer;
  private final boolean exposeDevelopmentTokens;
  private final ApplicationEventPublisher events;
  private final SecureRandom random = new SecureRandom();

  AuthService(
      UserRepository u,
      PasswordEncoder p,
      JwtEncoder e,
      RefreshTokenRepository r,
      ActionTokenRepository a,
      ApplicationEventPublisher events,
      @Value("${ticketforge.jwt.ttl}") Duration t,
      @Value("${ticketforge.jwt.issuer}") String i,
      @Value("${ticketforge.auth.expose-development-tokens:false}")
          boolean exposeDevelopmentTokens) {
    users = u;
    passwords = p;
    encoder = e;
    refreshTokens = r;
    actionTokens = a;
    this.events = events;
    ttl = t;
    issuer = i;
    this.exposeDevelopmentTokens = exposeDevelopmentTokens;
  }

  @Transactional
  public AuthController.RegistrationResponse register(AuthController.RegisterRequest r) {
    if (r.role() == UserRole.ADMIN)
      throw new ApiException(HttpStatus.BAD_REQUEST, "Public admin registration is not allowed");
    if (users.existsByEmailIgnoreCase(r.email()))
      throw ApiException.conflict("Email is already registered");
    UserAccount u =
        users.save(
            new UserAccount(r.email(), passwords.encode(r.password()), r.displayName(), r.role()));
    String raw = randomToken();
    actionTokens.save(
        new ActionToken(
            u,
            hash(raw),
            ActionToken.Type.EMAIL_VERIFICATION,
            Instant.now().plus(Duration.ofHours(24))));
    events.publishEvent(new NotificationEvents.VerifyEmail(u.getEmail(), u.getDisplayName(), raw));
    return new AuthController.RegistrationResponse(
        u.getId(), "EMAIL_VERIFICATION_REQUIRED", exposeDevelopmentTokens ? raw : null);
  }

  @Transactional
  public void verifyEmail(String raw) {
    ActionToken t =
        actionTokens
            .findByTokenHashAndType(hash(raw), ActionToken.Type.EMAIL_VERIFICATION)
            .orElseThrow(
                () -> new ApiException(HttpStatus.BAD_REQUEST, "Invalid verification token"));
    if (!t.usable(Instant.now()))
      throw new ApiException(HttpStatus.BAD_REQUEST, "Verification token expired or used");
    t.getUser().verifyEmail();
    t.use();
  }

  @Transactional(noRollbackFor = ApiException.class)
  public AuthController.AuthResponse login(AuthController.LoginRequest r) {
    UserAccount u = users.findByEmailIgnoreCase(r.email()).orElseThrow(this::invalid);
    Instant now = Instant.now();
    if (u.locked(now))
      throw new ApiException(HttpStatus.TOO_MANY_REQUESTS, "Account is temporarily locked");
    if (!passwords.matches(r.password(), u.getPasswordHash())) {
      u.recordFailedLogin(now);
      throw invalid();
    }
    if (!u.isEmailVerified()) throw ApiException.forbidden("Verify your email before signing in");
    if (u.getRole() == UserRole.ORGANIZER && !u.isOrganizerApproved())
      throw ApiException.forbidden("Organizer account is awaiting approval");
    u.recordSuccessfulLogin();
    return issue(u);
  }

  @Transactional
  public AuthController.AuthResponse refresh(String raw) {
    RefreshToken old = refreshTokens.findByTokenHash(hash(raw)).orElseThrow(this::invalid);
    if (!old.active(Instant.now())) throw invalid();
    old.revoke();
    return issue(old.getUser());
  }

  @Transactional
  public void logout(String raw) {
    refreshTokens.findByTokenHash(hash(raw)).ifPresent(RefreshToken::revoke);
  }

  @Transactional
  public AuthController.TokenDelivery requestPasswordReset(String email) {
    Optional<UserAccount> u = users.findByEmailIgnoreCase(email);
    if (u.isEmpty())
      return new AuthController.TokenDelivery(
          "If the account exists, reset instructions were created", null);
    String raw = randomToken();
    actionTokens.save(
        new ActionToken(
            u.get(),
            hash(raw),
            ActionToken.Type.PASSWORD_RESET,
            Instant.now().plus(Duration.ofMinutes(30))));
    events.publishEvent(
        new NotificationEvents.ResetPassword(u.get().getEmail(), u.get().getDisplayName(), raw));
    return new AuthController.TokenDelivery(
        "If the account exists, reset instructions were created",
        exposeDevelopmentTokens ? raw : null);
  }

  @Transactional
  public void resetPassword(String raw, String pass) {
    ActionToken t =
        actionTokens
            .findByTokenHashAndType(hash(raw), ActionToken.Type.PASSWORD_RESET)
            .orElseThrow(() -> new ApiException(HttpStatus.BAD_REQUEST, "Invalid reset token"));
    if (!t.usable(Instant.now()))
      throw new ApiException(HttpStatus.BAD_REQUEST, "Reset token expired or used");
    t.getUser().changePassword(passwords.encode(pass));
    refreshTokens.findByUserId(t.getUser().getId()).forEach(RefreshToken::revoke);
    t.use();
  }

  @Transactional
  public void approveOrganizer(UUID id) {
    UserAccount u = users.findById(id).orElseThrow(() -> ApiException.notFound("User not found"));
    if (u.getRole() != UserRole.ORGANIZER)
      throw new IllegalArgumentException("User is not an organizer");
    u.approveOrganizer();
  }

  private AuthController.AuthResponse issue(UserAccount u) {
    Instant now = Instant.now();
    JwtClaimsSet c =
        JwtClaimsSet.builder()
            .issuer(issuer)
            .issuedAt(now)
            .expiresAt(now.plus(ttl))
            .subject(u.getId().toString())
            .claim("email", u.getEmail())
            .claim("roles", List.of(u.getRole().name()))
            .claim("ver", u.getTokenVersion())
            .build();
    String access =
        encoder
            .encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(), c))
            .getTokenValue();
    String raw = randomToken();
    refreshTokens.save(new RefreshToken(u, hash(raw), now.plus(Duration.ofDays(30))));
    return new AuthController.AuthResponse(
        access, raw, "Bearer", ttl.toSeconds(), u.getId(), u.getRole());
  }

  private ApiException invalid() {
    return new ApiException(HttpStatus.UNAUTHORIZED, "Invalid credentials or token");
  }

  private String randomToken() {
    byte[] b = new byte[32];
    random.nextBytes(b);
    return Base64.getUrlEncoder().withoutPadding().encodeToString(b);
  }

  private String hash(String raw) {
    try {
      return HexFormat.of()
          .formatHex(
              MessageDigest.getInstance("SHA-256").digest(raw.getBytes(StandardCharsets.UTF_8)));
    } catch (NoSuchAlgorithmException e) {
      throw new IllegalStateException(e);
    }
  }
}

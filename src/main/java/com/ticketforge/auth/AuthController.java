package com.ticketforge.auth;

import com.ticketforge.user.UserRole;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.util.UUID;
import org.springframework.http.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {
  private final AuthService service;

  AuthController(AuthService s) {
    service = s;
  }

  @PostMapping("/register")
  ResponseEntity<RegistrationResponse> register(@Valid @RequestBody RegisterRequest r) {
    return ResponseEntity.status(HttpStatus.CREATED).body(service.register(r));
  }

  @PostMapping("/verify-email")
  ResponseEntity<Void> verify(@Valid @RequestBody TokenRequest r) {
    service.verifyEmail(r.token());
    return ResponseEntity.noContent().build();
  }

  @PostMapping("/login")
  AuthResponse login(@Valid @RequestBody LoginRequest r) {
    return service.login(r);
  }

  @PostMapping("/refresh")
  AuthResponse refresh(@Valid @RequestBody TokenRequest r) {
    return service.refresh(r.token());
  }

  @PostMapping("/logout")
  ResponseEntity<Void> logout(@Valid @RequestBody TokenRequest r) {
    service.logout(r.token());
    return ResponseEntity.noContent().build();
  }

  @PostMapping("/password-reset/request")
  TokenDelivery requestReset(@Valid @RequestBody ResetRequest r) {
    return service.requestPasswordReset(r.email());
  }

  @PostMapping("/password-reset/confirm")
  ResponseEntity<Void> reset(@Valid @RequestBody ResetConfirm r) {
    service.resetPassword(r.token(), r.newPassword());
    return ResponseEntity.noContent().build();
  }

  @PostMapping("/organizers/{id}/approve")
  @PreAuthorize("hasRole('ADMIN')")
  ResponseEntity<Void> approve(@PathVariable UUID id) {
    service.approveOrganizer(id);
    return ResponseEntity.noContent().build();
  }

  public record RegisterRequest(
      @NotBlank @Email String email,
      @Size(min = 10, max = 72) String password,
      @NotBlank @Size(max = 120) String displayName,
      @NotNull UserRole role) {}

  public record LoginRequest(@NotBlank @Email String email, @NotBlank String password) {}

  public record TokenRequest(@NotBlank String token) {}

  public record ResetRequest(@NotBlank @Email String email) {}

  public record ResetConfirm(
      @NotBlank String token, @Size(min = 10, max = 72) String newPassword) {}

  public record RegistrationResponse(UUID userId, String status, String verificationToken) {}

  public record TokenDelivery(String message, String developmentToken) {}

  public record AuthResponse(
      String accessToken,
      String refreshToken,
      String tokenType,
      long expiresIn,
      UUID userId,
      UserRole role) {}
}

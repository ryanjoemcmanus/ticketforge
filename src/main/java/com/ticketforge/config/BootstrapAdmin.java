package com.ticketforge.config;

import com.ticketforge.user.UserAccount;
import com.ticketforge.user.UserRepository;
import com.ticketforge.user.UserRole;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class BootstrapAdmin implements ApplicationRunner {
  private static final Logger log = LoggerFactory.getLogger(BootstrapAdmin.class);

  private final UserRepository users;
  private final PasswordEncoder passwordEncoder;
  private final String email;
  private final String password;

  public BootstrapAdmin(
      UserRepository users,
      PasswordEncoder passwordEncoder,
      @Value("${ticketforge.bootstrap-admin.email:}") String email,
      @Value("${ticketforge.bootstrap-admin.password:}") String password) {
    this.users = users;
    this.passwordEncoder = passwordEncoder;
    this.email = email.trim();
    this.password = password;
  }

  @Override
  @Transactional
  public void run(ApplicationArguments args) {
    if (email.isBlank() && password.isBlank()) return;
    if (email.isBlank() || password.isBlank()) {
      throw new IllegalStateException(
          "BOOTSTRAP_ADMIN_EMAIL and BOOTSTRAP_ADMIN_PASSWORD must be configured together");
    }
    if (password.length() < 12) {
      throw new IllegalStateException(
          "BOOTSTRAP_ADMIN_PASSWORD must contain at least 12 characters");
    }
    if (users.existsByRole(UserRole.ADMIN)) {
      log.info("Admin bootstrap skipped because an admin already exists");
      return;
    }
    if (users.existsByEmailIgnoreCase(email)) {
      throw new IllegalStateException(
          "Bootstrap admin email is already assigned to another account");
    }

    var admin =
        new UserAccount(
            email, passwordEncoder.encode(password), "TicketForge Admin", UserRole.ADMIN);
    admin.verifyEmail();
    users.save(admin);
    log.info("Bootstrapped initial administrator account email={}", email);
  }
}

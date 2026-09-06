package com.ticketforge.user;

import static org.assertj.core.api.Assertions.*;

import java.time.Instant;
import org.junit.jupiter.api.Test;

class UserAccountSecurityTest {
  @Test
  void locksAfterFiveFailuresAndSuccessfulLoginClearsState() {
    UserAccount user = new UserAccount("a@b.com", "hash", "A", UserRole.CUSTOMER);
    Instant now = Instant.now();
    for (int i = 0; i < 5; i++) user.recordFailedLogin(now);
    assertThat(user.locked(now)).isTrue();
    assertThat(user.getFailedLoginAttempts()).isEqualTo(5);
    user.recordSuccessfulLogin();
    assertThat(user.locked(now)).isFalse();
  }

  @Test
  void organizersRequireApproval() {
    UserAccount user = new UserAccount("o@b.com", "hash", "O", UserRole.ORGANIZER);
    assertThat(user.isOrganizerApproved()).isFalse();
    user.approveOrganizer();
    assertThat(user.isOrganizerApproved()).isTrue();
  }
}

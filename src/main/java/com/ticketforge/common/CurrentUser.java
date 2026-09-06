package com.ticketforge.common;

import java.util.UUID;
import org.springframework.security.core.Authentication;

public final class CurrentUser {
  private CurrentUser() {}

  public static UUID id(Authentication auth) {
    if (auth == null || !auth.isAuthenticated())
      throw ApiException.forbidden("Authentication required");
    return UUID.fromString(auth.getName());
  }
}

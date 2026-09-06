package com.ticketforge.auth;

import java.util.*;
import org.springframework.data.jpa.repository.*;

public interface RefreshTokenRepository extends JpaRepository<RefreshToken, UUID> {
  Optional<RefreshToken> findByTokenHash(String hash);

  List<RefreshToken> findByUserId(UUID userId);
}

package com.ticketforge.auth;

import java.util.*;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ActionTokenRepository extends JpaRepository<ActionToken, UUID> {
  Optional<ActionToken> findByTokenHashAndType(String hash, ActionToken.Type type);
}

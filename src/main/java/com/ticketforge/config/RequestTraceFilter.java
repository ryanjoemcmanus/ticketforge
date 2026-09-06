package com.ticketforge.config;

import jakarta.servlet.*;
import jakarta.servlet.http.*;
import java.io.IOException;
import java.util.UUID;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
public class RequestTraceFilter extends OncePerRequestFilter {
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain chain)
      throws ServletException, IOException {
    String trace = OptionalHeader.value(request.getHeader("X-Request-Id"));
    MDC.put("traceId", trace);
    response.setHeader("X-Request-Id", trace);
    try {
      chain.doFilter(request, response);
    } finally {
      MDC.remove("traceId");
    }
  }

  private static final class OptionalHeader {
    static String value(String value) {
      if (value == null || value.isBlank()) return UUID.randomUUID().toString();
      String candidate = value.substring(0, Math.min(value.length(), 100));
      return candidate.matches("[A-Za-z0-9._-]+") ? candidate : UUID.randomUUID().toString();
    }
  }
}

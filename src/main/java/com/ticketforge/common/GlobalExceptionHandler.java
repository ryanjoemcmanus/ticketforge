package com.ticketforge.common;

import jakarta.servlet.http.HttpServletRequest;
import java.net.URI;
import java.time.Instant;
import java.util.*;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.*;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.*;

@RestControllerAdvice
public class GlobalExceptionHandler {
  @ExceptionHandler(ApiException.class)
  ProblemDetail api(ApiException ex, HttpServletRequest request) {
    return problem(ex.getStatus(), ex.getMessage(), request);
  }

  @ExceptionHandler(MethodArgumentNotValidException.class)
  ProblemDetail validation(MethodArgumentNotValidException ex, HttpServletRequest request) {
    ProblemDetail p = problem(HttpStatus.BAD_REQUEST, "Request validation failed", request);
    Map<String, String> errors = new LinkedHashMap<>();
    ex.getBindingResult()
        .getFieldErrors()
        .forEach(e -> errors.putIfAbsent(e.getField(), e.getDefaultMessage()));
    p.setProperty("errors", errors);
    return p;
  }

  @ExceptionHandler(IllegalArgumentException.class)
  ProblemDetail badRequest(IllegalArgumentException ex, HttpServletRequest request) {
    return problem(HttpStatus.BAD_REQUEST, ex.getMessage(), request);
  }

  @ExceptionHandler(DataIntegrityViolationException.class)
  ProblemDetail conflict(DataIntegrityViolationException ex, HttpServletRequest request) {
    return problem(HttpStatus.CONFLICT, "The request conflicts with an existing record", request);
  }

  private ProblemDetail problem(HttpStatus status, String detail, HttpServletRequest request) {
    ProblemDetail p = ProblemDetail.forStatusAndDetail(status, detail);
    p.setTitle(status.getReasonPhrase());
    p.setInstance(URI.create(request.getRequestURI()));
    p.setProperty("timestamp", Instant.now());
    return p;
  }
}

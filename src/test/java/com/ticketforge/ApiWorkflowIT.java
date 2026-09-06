package com.ticketforge;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.ticketforge.auth.ActionTokenRepository;
import com.ticketforge.auth.RefreshTokenRepository;
import com.ticketforge.catalog.*;
import com.ticketforge.common.AuditEventRepository;
import com.ticketforge.order.OrderRepository;
import com.ticketforge.order.TicketRepository;
import com.ticketforge.reservation.SeatHoldRepository;
import com.ticketforge.user.UserAccount;
import com.ticketforge.user.UserRepository;
import com.ticketforge.user.UserRole;
import java.time.Instant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.testcontainers.junit.jupiter.Testcontainers;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
@Testcontainers(disabledWithoutDocker = true)
class ApiWorkflowIT {
  @Autowired MockMvc http;
  @Autowired ObjectMapper json;
  @Autowired PasswordEncoder passwords;
  @Autowired UserRepository users;
  @Autowired VenueRepository venues;
  @Autowired SeatRepository seats;
  @Autowired EventRepository events;
  @Autowired EventSeatRepository inventory;
  @Autowired SeatHoldRepository holds;
  @Autowired OrderRepository orders;
  @Autowired TicketRepository tickets;
  @Autowired RefreshTokenRepository refreshTokens;
  @Autowired ActionTokenRepository actionTokens;
  @Autowired AuditEventRepository audit;

  @BeforeEach
  void clean() {
    audit.deleteAll();
    tickets.deleteAll();
    orders.deleteAll();
    inventory.deleteAll();
    holds.deleteAll();
    events.deleteAll();
    seats.deleteAll();
    venues.deleteAll();
    refreshTokens.deleteAll();
    actionTokens.deleteAll();
    users.deleteAll();
  }

  @Test
  void completeRestWorkflowFromOnboardingThroughRefund() throws Exception {
    JsonNode organizerRegistration =
        postJson(
            "/api/v1/auth/register",
            """
            {"email":"owner@example.com","password":"StrongPass123!","displayName":"Owner","role":"ORGANIZER"}
            """,
            null,
            201);
    verify(organizerRegistration.get("verificationToken").asText());

    UserAccount admin =
        new UserAccount(
            "admin@example.com", passwords.encode("StrongPass123!"), "Admin", UserRole.ADMIN);
    admin.verifyEmail();
    users.save(admin);
    String adminToken = login("admin@example.com");
    http.perform(
            post(
                    "/api/v1/auth/organizers/{id}/approve",
                    organizerRegistration.get("userId").asText())
                .header("Authorization", bearer(adminToken)))
        .andExpect(status().isNoContent());
    String organizerToken = login("owner@example.com");

    JsonNode venue =
        postJson(
            "/api/v1/venues",
            """
            {"name":"API Arena","address":"1 Test Way","city":"Pittsburgh","timezone":"America/New_York"}
            """,
            organizerToken,
            201);
    String venueId = venue.get("id").asText();
    postJson(
        "/api/v1/venues/" + venueId + "/seats",
        """
        [{"section":"FLOOR","row":"A","number":"1"},{"section":"FLOOR","row":"A","number":"2"}]
        """,
        organizerToken,
        201);
    JsonNode event =
        postJson(
            "/api/v1/events",
            """
            {"venueId":"%s","title":"API Tour","description":"End-to-end contract test","startsAt":"%s","category":"MUSIC","seatPrice":65.00}
            """
                .formatted(venueId, Instant.now().plusSeconds(604800)),
            organizerToken,
            201);
    String eventId = event.get("id").asText();
    http.perform(
            post("/api/v1/events/{id}/publish", eventId)
                .header("Authorization", bearer(organizerToken)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("PUBLISHED"));

    JsonNode customerRegistration =
        postJson(
            "/api/v1/auth/register",
            """
            {"email":"buyer@example.com","password":"StrongPass123!","displayName":"Buyer","role":"CUSTOMER"}
            """,
            null,
            201);
    verify(customerRegistration.get("verificationToken").asText());
    String customerToken = login("buyer@example.com");

    MvcResult detailsResult =
        http.perform(get("/api/v1/events/{id}", eventId))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.inventory.length()").value(2))
            .andReturn();
    JsonNode details = json.readTree(detailsResult.getResponse().getContentAsString());
    String eventSeatId = details.at("/inventory/0/id").asText();
    JsonNode hold =
        postJson(
            "/api/v1/holds", "{\"eventSeatIds\":[\"" + eventSeatId + "\"]}", customerToken, 201);
    String holdId = hold.get("id").asText();
    MvcResult checkoutResult =
        http.perform(
                post("/api/v1/orders")
                    .header("Authorization", bearer(customerToken))
                    .header("Idempotency-Key", "browser-checkout-1")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"holdId\":\"" + holdId + "\",\"paymentToken\":\"tok_visa\"}"))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.status").value("PAYMENT_CONFIRMED"))
            .andExpect(jsonPath("$.tickets.length()").value(1))
            .andReturn();
    String orderId =
        json.readTree(checkoutResult.getResponse().getContentAsString()).get("id").asText();
    http.perform(
            post("/api/v1/orders/{id}/cancel", orderId)
                .header("Authorization", bearer(customerToken))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"reason\":\"Plans changed\"}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("REFUNDED"))
        .andExpect(jsonPath("$.tickets[0].status").value("CANCELLED"));

    assertThat(inventory.findById(java.util.UUID.fromString(eventSeatId)).orElseThrow().getStatus())
        .isEqualTo(EventSeatStatus.AVAILABLE);
  }

  @Test
  void staticClientSecurityHeadersAndProblemDetailsArePubliclyAvailable() throws Exception {
    http.perform(get("/")).andExpect(status().isOk()).andExpect(forwardedUrl("index.html"));
    http.perform(get("/index.html"))
        .andExpect(status().isOk())
        .andExpect(header().string("Content-Security-Policy", org.hamcrest.Matchers.notNullValue()))
        .andExpect(content().string(org.hamcrest.Matchers.containsString("TicketForge")));
    http.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON).content("{}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.title").value("Bad Request"))
        .andExpect(jsonPath("$.errors.email").exists());
  }

  private JsonNode postJson(String path, String body, String token, int expected) throws Exception {
    var request = post(path).contentType(MediaType.APPLICATION_JSON).content(body);
    if (token != null) request.header("Authorization", bearer(token));
    MvcResult result = http.perform(request).andExpect(status().is(expected)).andReturn();
    return json.readTree(result.getResponse().getContentAsString());
  }

  private void verify(String token) throws Exception {
    postJson("/api/v1/auth/verify-email", "{\"token\":\"" + token + "\"}", null, 204);
  }

  private String login(String email) throws Exception {
    return postJson(
            "/api/v1/auth/login",
            "{\"email\":\"" + email + "\",\"password\":\"StrongPass123!\"}",
            null,
            200)
        .get("accessToken")
        .asText();
  }

  private String bearer(String token) {
    return "Bearer " + token;
  }
}

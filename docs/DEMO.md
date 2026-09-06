# TicketForge demo guide

TicketForge is a production-style Java ticketing reference implementation that prevents a seat from being sold twice, even when 50 buyers attempt to reserve it simultaneously.

## Suggested walkthrough

1. Start the application with `docker compose up --build`.
2. Open `http://localhost:8080` and browse the seeded events.
3. Sign in as `customer@ticketforge.local` with `DemoPass123!`.
4. Select a seat, create a ten-minute hold, and complete mock checkout.
5. Open the customer dashboard to view the issued ticket and order history.
6. Sign in as `organizer@ticketforge.local` to view event capacity, held seats, sales, and revenue.
7. Open `ReservationConcurrencyIT` to see the synchronized 50-request race and exactly-one-winner assertion.
8. Review the Flyway migrations for the inventory, ticket, and idempotency constraints.

## Architecture notes

- A modular monolith keeps transactions and operational ownership straightforward while retaining clear capability boundaries.
- Scheduled hold cleanup improves availability, but correctness comes from row locks, database constraints, and transactional state transitions.
- Customer-scoped idempotency keys make checkout retries safe after ambiguous network failures.
- Ownership is checked in service-layer use cases as well as at endpoint role boundaries.
- The default mock gateway accepts opaque tokens only. The optional Stripe adapter rejects live keys and verifies webhook signatures.
- SMTP and payment integrations sit behind narrow interfaces so local runs remain deterministic.

## Demo accounts

Demo seeding is opt-in and must remain disabled outside local development.

| Role | Email | Password |
|---|---|---|
| Customer | `customer@ticketforge.local` | `DemoPass123!` |
| Organizer | `organizer@ticketforge.local` | `DemoPass123!` |
| Administrator | `admin@ticketforge.local` | `DemoPass123!` |

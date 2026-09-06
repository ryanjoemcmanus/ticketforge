# Ticket Central V1: codebase analysis

The uploaded V1 is a 1,693-line, 12-file default-package Java console application. It has no build descriptor, external dependencies, database, HTTP layer, or framework. `Main` constructs `TicketingApplication`; that class owns the terminal menus, every in-memory collection, ID counters, lookups, sample data, and orchestration.

## Class inventory

| Class | Responsibility in V1 |
|---|---|
| `Main` | Three-line console entry point. |
| `TicketingApplication` | Composition root, CLI, input validation, in-memory storage, sample records, customer/admin flows, and reporting. |
| `UserAccount` | Username/password/role plus a mutable process-local `loggedIn` flag. |
| `Customer` | Profile data and an in-memory list of purchased `Ticket` objects. |
| `Admin` | Account wrapper and an in-memory list of managed `Event` objects. |
| `Event` | Name/date/location/price and in-memory `Seat`/`Ticket` lists. |
| `Seat` | Seat label/section and one availability boolean. |
| `Ticket` | Links a customer, event, seat, and payment; tracks `PENDING`, `CONFIRMED`, or `CANCELLED`. |
| `Payment` | Validates an exact amount and transitions through `PENDING`, `FAILED`, `CONFIRMED`, and `REFUNDED`. |
| `Receipt` | Immutable-ish purchase snapshot printed to the terminal. |
| `TicketingService` | Coordinates immediate reservation, payment, ticket creation, rollback, and cancellation. |
| `TicketingApplicationTest` | Custom main-method harness with 18 integration-style checks; it is not JUnit. |

The archive also contains a class diagram, test-output transcript, presentation, and project report.

## Existing behavior

- Customers register, log in, browse events/seats, purchase, list, and cancel tickets.
- Admins log in, create/update/remove owned events, add seats, and view simple ticket-sales totals.
- V1 seeds an admin, customer, two events, and six seats for demonstration.
- Seat labels are unique within an event (case-insensitive).
- Purchase validates login, event membership, price, seat availability, and payment method.
- Failed payment releases the seat; unexpected receipt failure reverses the object mutations.
- Cancellation refunds a confirmed payment, releases the seat, and removes customer/event ticket references.
- The 18 checks cover accounts, profiles, admin ownership, duplicate seats, purchase validation, rollback, receipts, and cancellation.

## What is worth preserving

The domain narrative is sound: users have roles, organizers own events, events expose seat inventory, a purchase produces payment/order evidence and a ticket, and cancellation changes several related records together. V1 already recognizes two important invariants: an organizer may manage only owned events, and a failed purchase must not strand unavailable inventory.

## Why the architecture cannot simply be extended

- All state disappears on exit and is duplicated across mutable lists.
- Passwords are stored and compared as plain text; authentication is a boolean on a shared object.
- IDs are process-local integers and will collide across instances.
- Dates and event locations are unvalidated strings; money is `double`.
- A seat has only `available`; there is no distinction between a temporary hold and a completed sale.
- `reserveSeat()` is not thread-safe and provides no protection across application instances.
- Purchase changes several objects without a real atomic transaction.
- The CLI, application logic, domain model, persistence, and presentation concerns are interleaved.
- Returning defensive list copies prevents direct list corruption but does not protect the mutable elements.
- Error results are strings rather than a stable API contract.
- Tests cannot run under standard Java test tooling or against production-like infrastructure.

## V1 to V2 mapping

| V1 idea | V2 design |
|---|---|
| `UserAccount`, `Customer`, `Admin` | One persisted user plus `CUSTOMER`, `ORGANIZER`, and `ADMIN` roles; BCrypt passwords and signed JWTs. |
| Event `location` string | Normalized `Venue` and reusable physical `Seat` records. |
| Event-owned seat boolean | Event-specific inventory with `AVAILABLE → HELD → SOLD`. |
| Immediate purchase | Explicit expiring hold, then idempotent checkout. |
| `Payment` and `Receipt` | Token-only payment gateway boundary and durable order/payment reference. |
| Ticket object | Persisted, uniquely coded issued ticket. |
| Object rollback | PostgreSQL transaction rollback and database constraints. |
| Linear scans | Indexed repository queries and paginated event browsing. |
| Custom checks | JUnit, Mockito/AssertJ, and PostgreSQL Testcontainers tests. |

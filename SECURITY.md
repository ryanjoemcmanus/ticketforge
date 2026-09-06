# Security policy

## Supported versions

Security fixes are applied to the latest revision on the default branch.

## Reporting a vulnerability

Please use GitHub's private vulnerability reporting feature instead of opening a public issue. Include the affected endpoint or component, reproduction steps, and potential impact. Do not include real credentials, payment details, or personal data.

## Scope and operating assumptions

TicketForge is a reference implementation, not a hosted payment processor. The default payment adapter uses opaque mock tokens, and the optional Stripe adapter accepts test-mode keys only. Production operators are responsible for TLS termination, secret management, rate limiting, database backups, monitoring, and provider-specific compliance.

Never commit `.env` files or credentials. Development token exposure and demo data must remain disabled outside local environments.

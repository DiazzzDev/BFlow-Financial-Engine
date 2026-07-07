# ADR-0003: Idempotency Key Enforcement for Financial Write Operations

- **Status:** Accepted
- **Date:** 2026-07-06

## Context

BFlow's core domain revolves around money movement: expenses, incomes, and transfers between wallets. All of these operations mutate a wallet's balance as a side effect of persisting a financial record.

HTTP clients (mobile apps, web frontends) cannot always guarantee that a request reaches the server exactly once. Network timeouts, dropped connections, client crashes mid-request, and automatic retries at the infrastructure level (load balancers, proxies) can all cause the same logical operation to be sent more than once.

For a read-only endpoint, a duplicate request is harmless. For a financial write operation, a duplicate request means real money is created, destroyed, or moved twice. Specifically:

- A duplicated expense or income silently corrupts the wallet balance and any budget calculations derived from it.
- A duplicated transfer moves the same amount between two wallets twice, with no natural way to detect after the fact which execution was legitimate and which was a duplicate.

Neither `@Transactional` boundaries nor pessimistic locking on `Wallet` rows solve this problem, because each duplicate request is a separate, fully valid transaction from the database's point of view. Transactional integrity guarantees that a single request either fully succeeds or fully rolls back; it says nothing about whether the request should have been executed at all.

This risk will materially increase once BFlow integrates a payment gateway (Wompi) for subscription billing and wallet funding. Payment gateways are a canonical source of retried webhooks and client-side retries after ambiguous network failures, and a duplicated payment-triggered financial write is a direct monetary and reputational risk, not just a data-consistency one.

No mechanism currently exists in the codebase to detect that an incoming request is a retry of a previous one.

## Decision

BFlow requires an `Idempotency-Key` HTTP header on all `POST` requests targeting financial write operations:

- `POST /api/v1/expenses`
- `POST /api/v1/incomes`
- `POST /api/v1/transfers`

The key is a client-generated UUID (v4) that uniquely identifies one user-intended operation, **not** one HTTP request. Clients must reuse the same key across retries of the same logical operation and generate a new key for every new operation.

### Enforcement Mechanism

An `IdempotencyFilter` (`OncePerRequestFilter`), registered in the Spring Security filter chain immediately after `BearerTokenAuthenticationFilter`, intercepts requests to the protected endpoints and applies the following rules:

- **Missing header** → `400 Bad Request`. The request is rejected before reaching the controller.
- **New key** (no existing record for `idempotencyKey + userId + endpoint`) → the request proceeds normally. On a successful (`2xx`) response, the response body, HTTP status, and a SHA-256 hash of the request body are persisted together with the key.
- **Repeated key with identical request body** (hash match) → the previously persisted response is returned without executing the business logic again.
- **Repeated key with different request body** (hash mismatch) → `409 Conflict`. This indicates either incorrect client behavior or a potential integrity issue and must not be resolved automatically.

Idempotency records have a configurable TTL (`app.idempotency.ttl`, default **24 hours**). Expired records are removed by `IdempotencyCleanupTask` and are subsequently treated as non-existent, allowing the key to be reused.

## Scope Boundary

This mechanism protects against duplicate execution of the same client-originated request.

It is **not** a replacement for:

- Pessimistic or optimistic locking on `Wallet` entities.
- Deterministic lock ordering for multi-wallet operations such as transfers.
- Database transaction boundaries (`@Transactional`).

These mechanisms solve different consistency problems and remain independently required. This ADR addresses only duplicate-request detection.

## Consequences

### Positive

- Retried client requests (network timeouts, application crashes, accidental double submissions) no longer create duplicated financial records or inconsistent wallet balances.
- Provides a reusable foundation for future Wompi payment callbacks and other payment gateway integrations without requiring a separate deduplication mechanism.
- Detects incorrect client behavior (`409 Conflict`) instead of silently duplicating or overwriting financial operations.
- Centralizes idempotency enforcement in the transport layer, avoiding duplicated logic across services and controllers.

### Negative

- All clients (web and mobile) must generate and include an `Idempotency-Key` header for protected write operations. This is a breaking API change.
- Introduces a new persistence table (`idempotency_keys`) and a scheduled cleanup task.
- Incorrect client-side key reuse results in explicit `409 Conflict` responses, requiring proper client-side handling.
- Cached responses remain unchanged during the TTL window. If the API response structure evolves, previously cached responses continue using the format that existed when they were originally stored.

## Implementation Notes

- Endpoint protection is configured through a static `PROTECTED_PATHS` collection inside `IdempotencyFilter`. New financial write endpoints must be explicitly added.
- `IdempotencyFilter` is instantiated manually inside `SecurityConfig.filterChain(...)` instead of being registered as a `@Component` or servlet `Filter` bean. This prevents Spring Boot from automatically registering the filter twice (once in the servlet container and once in the Spring Security filter chain).
- Idempotency lookup is scoped by the tuple `(idempotencyKey, userId, endpoint)`. The same key value used by different users or different endpoints represents independent operations.
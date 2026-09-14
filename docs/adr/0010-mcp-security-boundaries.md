# ADR-0010: Security Boundaries for MCP Integration

- **Status:** Proposed
- **Date:** 2026-09-13
- **Resolves:** [#93 — Define MCP Security Boundaries](https://github.com/DiazzzDev/BFlow-Financial-Engine/issues/93)
- **Depends on:** ADR-0009 (Scope Model)
- **Folds in roadmap items:** #2 Security Boundaries, #3 Agent Actor,
  #4 Request Source — issue #93 bundles all three into one contract,
  and they genuinely can't be designed separately (you can't audit or
  gate by actor without the actor model existing first), so this ADR
  does the same.

## Context

ADR-0009 established *what an agent is allowed to ask for* (scopes) and
*where the resource decision happens* (unchanged, inside `ServiceWallet` /
`ServiceWalletSharing`). Since that ADR was written, `bflow-mcp` has
actually been built and proven end-to-end: it's a separate Spring Boot
service with no dependency on this repo's domain module, it validates
Cognito JWTs, enforces scopes via `RequiresScopeAspect`, and its first
tool (`listWallets`) successfully proxies a real user's request through
to `GET /api/v1/wallets` and back. This ADR updates the security-boundary
contract to match what exists, and closes the gaps issue #93 identifies
that the original draft left open.

## Decision

### 1. Security boundary diagram

Matches issue #93's expected architecture exactly, with one clarification
on the second hop:

```
External AI Client (ChatGPT / Claude / Gemini)
        │  MCP (Streamable HTTP)
        v
BFlow MCP  (bflow-mcp — separate service, separate repo)
  Protocol / Tool Mapping
  Authentication Context (Cognito JWT)
  Scope Enforcement (@RequiresScope)
        │  HTTPS, same bearer token, forwarded unchanged
        v
BFlow Application  (BFlow-Financial-Engine's public API)
  Controllers → Application Services → Authorization → Validation
        │
        v
BFlow Domain
  Business Rules / Invariants
        │
        v
Persistence
  PostgreSQL / Repositories
```

Clarification: `bflow-mcp` reaches "BFlow Application" over the network
(`BflowApiClient` → `GET/POST /api/v1/**`), not via an in-process method
call. This is a *stronger* guarantee than the issue's minimum bar — it's
not just that MCP "must not" access the database or bypass application
services, it *structurally cannot*: `bflow-mcp` doesn't depend on the
Maven module, the repositories, or the domain classes, so there's no
code path for it to take even by mistake.

### 2. Trust model per component

| Component | Trust level | Notes |
|---|---|---|
| External AI Client | **Untrusted** | May send malformed, retried, or out-of-scope requests. Never assumed safe for being from an "approved" provider. |
| BFlow MCP (`bflow-mcp`) | **Controlled boundary** | Validates requests, establishes the authenticated user context, enforces scopes, maps tools to API calls, returns structured errors, tags requests as agent-originated. **Never** touches the database, duplicates business rules, or bypasses the API. |
| BFlow Application (this repo's API) | **Trusted** | Owns authorization, business validation, use-case execution, transaction boundaries — unchanged by MCP's existence. |
| BFlow Domain | **Trusted** | Final authority on invariants (ownership, wallet permissions, transaction/budget/recurring rules). MCP has no path to override these — see §1. |
| Database | **Internal** | `bflow-mcp` has zero credentials or network path to it (ADR-0010 draft §1, unchanged). |

### 3. Authentication boundary

```
Agent Client → Authenticated BFlow User → Authorized Resources
```

The AI provider (Anthropic, OpenAI, Google, ...) is never the identity.
The **BFlow user** is always the principal — established via the Cognito
JWT `bflow-mcp` validates on every request (`SecurityConfig`), the same
user pool the web app uses, existing accounts only (no MCP-driven
sign-up, per Ed's stated constraint).

### 4. Authorization boundary — the required sequence

```
Authenticate (Cognito JWT, SecurityConfig)
    ↓
Resolve BFlow User (JWT subject)
    ↓
Validate requested scope (@RequiresScope / RequiresScopeAspect)
    ↓
Validate resource ownership/role (unchanged — ServiceWallet/WalletUser, inside the API)
    ↓
Validate business rules (unchanged — domain layer, inside the API)
    ↓
Execute operation
    ↓
Audit operation (§8 — not yet implemented, see §12)
```

Failure at any stage prevents execution. This is already true today for
the five steps that exist (`listWallets` fails closed on missing auth,
missing scope, or missing resource access); audit is the one link not
yet built.

### 5. Agent identity — `actorType` and `source`

Per issue #93's minimum model:

```
actorType: USER | AGENT | SYSTEM
source:    WEB  | MCP   | INTERNAL
```

Concretely, on every request `bflow-mcp` proxies to the API:

- `actorType = AGENT` (always, for anything coming through `bflow-mcp` —
  never `USER`, even though the resolved principal *is* a real user;
  `actorType` describes the interface, `userId` describes the principal)
- `source = MCP`
- `userId` = the authenticated BFlow user resolved from the JWT (§3) —
  unchanged, still the sole principal

Carried as a header (`X-BFlow-Actor-Type: AGENT`, `X-BFlow-Channel: mcp`
— renaming/extending the single-purpose header the original draft
proposed for rate-limiting alone) on every proxied call, so the API can
read it without decoding the JWT further. The AI provider's specific
identity (Claude vs. ChatGPT vs. Gemini) may be recorded as metadata
alongside this, but never replaces `userId` as the principal, and never
substitutes for `actorType`/`source`.

This header is **advisory input to audit and rate-limiting only** — it
must never be trusted for authorization decisions on its own (a
compromised `bflow-mcp` could send `actorType: USER` to hide itself);
authorization stays keyed on the validated JWT regardless of what this
header claims.

### 6. Financial operation boundary

Unchanged from the original draft, restated per issue #93's exact list —
`bflow-mcp` does not, and the first several issues of the roadmap will
not, expose:

```
money transfers
withdrawals
payments
bank account changes
```

Future high-risk operations require the confirmation flow issue #18
owns:

```
AI requests operation → BFlow evaluates risk → explicit user confirmation → BFlow executes
```

The AI is never the final authority on a high-risk action.

### 7. Data exposure boundary

`bflow-mcp` tool responses must not include:

```
cross-user data
unrelated wallet data
internal database identifiers where unnecessary
security metadata / authentication secrets
internal infrastructure information
stack traces / internal exception details
```

Current gap: `ListWalletsTool.listWallets()` returns the API's raw
`ApiResponse<Page<WalletResponse>>` JSON body untouched — which today
happens to be clean (no stack traces, no internal IDs beyond the
wallet's own UUID, which the user already owns), but nothing enforces
that going forward as more tools are added. Issue #9-12 (the rest of
the Read API) should design each tool's response shape deliberately,
not just forward whatever the underlying endpoint returns — flagged
here, owned there.

### 8. Error boundary

`bflow-mcp` must never leak `NullPointerException`, `HibernateException`,
SQL state, connection details, stack traces, or AWS
credentials/configuration to the AI client. It must return a structured
shape instead:

```json
{
  "code": "WALLET_ACCESS_DENIED",
  "message": "The authenticated user does not have permission to access this wallet.",
  "retryable": false
}
```

**Gap, not yet implemented:** neither `bflow-mcp` nor its tools have any
exception handling today. A downstream 403/500 from the BFlow API
currently propagates as whatever `RestClient`/Spring AI's default
exception shape produces — likely a raw stack trace to the MCP client
in some paths. This is the most concrete unfinished item in this ADR;
owned by roadmap issue #20 (Errors), which should introduce a
`@ControllerAdvice`-equivalent (or the MCP-tool error-mapping
equivalent Spring AI's `@Tool` methods support) translating BFlow API
error responses (their existing `ApiResponse` envelope) into this shape,
never re-throwing raw exceptions to the client.

### 9. Audit boundary

Every state-changing MCP operation must be traceable with, at minimum:

```
user, actorType, source, action, resource, timestamp, correlation ID, result
```

Example:

```
User: <BFlow User>
Actor: AGENT
Source: MCP
Action: CREATE_TRANSACTION
Resource: Transaction
Result: SUCCESS
```

**Gap, not yet implemented** — owned by roadmap issue #5 (Audit Events)
and #21 (Observability). Audit records must be generated by BFlow itself
(the API side, where the operation actually executes and where existing
persistence lives), never inferred from what the AI client claims it
did — `bflow-mcp` forwarding `actorType`/`source`/a correlation ID (§5)
is necessary input to this, but the write itself belongs to the API.

### 10. Rate-limit boundary (unchanged from the original draft)

```java
// EndpointPolicyResolver — one more branch, same shape as the others
if ("mcp".equals(request.getHeader("X-BFlow-Channel"))) {
    return "MCP_AUTHENTICATED_API";
}
```

```java
// RateLimitPolicyRegistry — a stricter, separate bucket for the MCP channel
"MCP_AUTHENTICATED_API",
new RateLimitPolicy(30, Duration.ofMinutes(3)),
```

Still keyed through the existing `UserKeyResolver` (same user, separate
bucket), so a runaway agent throttles itself without touching the human's
own web-session budget. `X-BFlow-Channel` is now defined once, in §5,
instead of being invented ad hoc here and again in the Agent Actor issue.

### 11. Network, credential, JWT-trust, and CORS boundaries

Unchanged from the original draft (§1-4 there): no VPC peering, no DB/S3
credentials on `bflow-mcp`'s task role, no static service-to-service
secret, local JWT validation recommended (still needs Ed's confirmation
against the real Cognito app client), and CORS is a non-issue for
server-to-server MCP calls but applies narrowly to any browser-hosted
part of the OAuth flow. See the original sections for full detail — not
repeated here since nothing about them changed.

### 12. What's already implemented vs. still a contract

Per issue #93's own "Explicit Non-Goals" (this issue defines the
contract, not the implementation), most of the above is intentionally
still a spec. Status as of this ADR:

| Acceptance criterion | Status |
|---|---|
| External AI clients classified as untrusted | §2 |
| MCP defined as a controlled security boundary | §1, §2 |
| Authentication responsibility documented | §3 — and implemented (`SecurityConfig`, Cognito JWT) |
| Authorization responsibility documented | §4 — and implemented for the 4 steps that exist |
| Resource-level authorization required after scope validation | implemented — unchanged BFlow API behavior |
| MCP cannot access repositories/database directly | structurally true — separate repo, no dependency |
| MCP cannot bypass application services/domain rules | structurally true — same reason |
| Agent-originated operations distinguishable from user-originated | implemented — `BflowApiClient` sends `X-BFlow-Channel`/`X-BFlow-Actor-Type`/`X-Correlation-Id` on every call |
| High-risk financial operations excluded from initial implementation | §6 — no transfer/payment tool exists |
| Agent-facing errors don't expose internals | implemented — `ToolErrorHandlingAspect` translates every exception to `{code, message, retryable}`, wrapping the scope gate too |
| MCP write operations auditable | **gap — §9, owned by issue #5/#21** (moot today: no write tools exist yet either) |

## Explicitly out of scope for this ADR

Matches issue #93's own non-goals list: MCP tools beyond `listWallets`,
OAuth endpoint implementation (done separately, see the OAuth proxy
work), Cognito configuration changes beyond what's already shipped,
AI-provider-specific integrations, transaction operations, automation
execution, money transfers, autonomous financial decision-making.

## Consequences

### Positive

- Every trusted/untrusted boundary in issue #93 has a concrete owner in
  this codebase or a concrete future issue — nothing left implicit.
- The dependency-direction guarantee (§1) is structural, not a rule
  someone has to remember to follow.
- `actorType`/`source` (§5) gives audit (§9) and rate-limiting (§10) a
  single shared header instead of three issues each inventing their own.

### Negative / open work

- §9 (audit) can't be implemented on the BFlow API side until this ADR
  is approved — the header contract (§5) is stable and already being
  sent, so that half of the sequencing risk is gone.
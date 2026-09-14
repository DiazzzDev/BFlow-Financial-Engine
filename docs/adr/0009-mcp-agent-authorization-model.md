# ADR-0009: Agent Access Scope Model for MCP Integration

- **Status:** Proposed
- **Date:** 2026-09-12
- **Resolves:** [#92 — Define Agent Access Scope Model](https://github.com/DiazzzDev/BFlow-Financial-Engine/issues/92)

## Context

BFlow's REST API is a stateless resource server: every request carries a
Cognito-issued JWT, `SecurityConfig` validates it via
`oauth2ResourceServer().jwt(...)`, and `CurrentUserService` resolves the
authenticated `User` from the JWT subject. Resource-level authorization is
**not** centralized in a filter — it lives inside each application service.
`ServiceWallet` and `ServiceWalletSharing`, for example, load the
`WalletUser` row for `(walletId, userId)` and throw `AccessDeniedException`
when the row is missing or the `WalletRole` (`OWNER` / `MEMBER`) doesn't
allow the operation.

This matters for the MCP design: **there is already exactly one place where
"can this user touch this wallet/transaction/budget?" is decided**, and it's
the service layer, not the controller. That is the seam MCP must plug into.

The long-term goal (tracked separately from this ADR) is for a user to talk
to an AI agent of their choice ("crea un presupuesto para la semana",
"me compré un teléfono", "invita a alguien a mi wallet", "¿cuál es mi
situación financiera?") and have the agent call BFlow on their behalf. This
ADR only defines the **authorization contract** that any future MCP tool
must obey. No MCP tool is implemented here.

## Decision

### 1. The agent is a client, not a role — and MCP is a separate service

MCP is deployed as its **own service** (`bflow-mcp`), not as a module
inside the existing Spring Boot app. It never autowires `Service*` beans
or touches the database — it is an HTTP client of BFlow's existing
`/api/v1/**` REST API, exactly like the web frontend is. This is the
standard shape for a remote MCP server per the MCP Authorization
specification (OAuth 2.1, resource server pattern, Streamable HTTP
transport) and it also makes the issue's own requirement literal instead
of just conventional: *"the external AI agent must be treated as another
client of BFlow, not as a privileged internal component."* An out-of-process
client physically cannot reach a repository or bypass a controller — there
is no other door.

Cognito remains the single Authorization Server for the whole system.
`bflow-mcp` does not issue or mint tokens; it validates the same
Cognito-issued JWT the API validates, and forwards it (or the same bearer
value) on the downstream call.

```
AI Client → bflow-mcp (own ECS service)   →   BFlow API (unchanged)
              |                                    |
     scope gate (new, here)          resource gate (existing, unchanged:
     validates JWT + `scope` claim    WalletUser / WalletRole lookup inside
     before proxying the call         ServiceWallet / ServiceWalletSharing)
```

**Deployment topology** (mirrors the existing pattern in `infra/`):
- New ECS Fargate service `bflow-mcp`, own task definition, own
  Cloudflare-routed subdomain (e.g. `mcp.bflow-studio.com`), same
  GitHub Actions CI/CD pipeline shape already used for the main app.
- No new database, no new IAM trust — it calls the public API over HTTPS
  like any other client and holds no long-lived secrets beyond what's
  needed to validate Cognito's JWKS.

### 2. Scopes

Scopes gate *operation type*, not tool names, so tools can be renamed or
multiplied later without touching the contract.

**Read scopes**
```
wallets:read
transactions:read
budgets:read
recurring:read
automations:read
```

**Write scopes**
```
transactions:write
budgets:write
recurring:write
automations:write
```

A scope is necessary but never sufficient. `transactions:write` lets an
agent call "create/update an expense or income," never "execute a
transfer" — transfers are a distinct capability, explicitly excluded below.

### 3. Explicitly out of scope (v1)

No scope will be minted for, and no MCP tool will ever call:

```
money transfers                  (ServiceTransfers.* is unreachable from MCP)
bank withdrawals / payment execution
authentication or security-setting changes
account ownership changes / account deletion
administrative or IAM/AWS operations
credentials, Cognito tokens, refresh tokens
direct database or repository access
other users' data
```

These require a separate, stricter authorization model later (likely
step-up auth / explicit confirmation), and are out of scope for this ADR.

### 4. Scope vs. resource authorization — reusing what exists

Scope check and resource check are two independent gates, executed in this
order for every MCP tool call:

1. **Scope gate (new, lives in `bflow-mcp`):** does the token's `scope`
   claim contain the scope required by this tool? Rejected → the MCP call
   fails before any HTTP request to the BFlow API is even made.
2. **Resource gate (existing, unchanged, lives in the BFlow API):** the
   BFlow API receives the proxied request with the same bearer token,
   resolves the same `User` via `CurrentUserService`, and the same
   `Service*` method performs the same `WalletUser`/`WalletRole` lookup it
   already performs for the web client, throwing the same
   `AccessDeniedException` on failure.

Concretely, an MCP tool handler is a thin HTTP client, not a bean that
touches domain code:

```java
package bflow.mcp.tools;

import bflow.mcp.client.BflowApiClient;
import bflow.mcp.security.RequiresScope;
import bflow.mcp.security.McpAuthContext;

public class CreateBudgetTool {

    private final BflowApiClient bflowApiClient;

    public CreateBudgetTool(BflowApiClient bflowApiClient) {
        this.bflowApiClient = bflowApiClient;
    }

    @RequiresScope("budgets:write")
    public BudgetResponse handle(BudgetRequest request,
                                  McpAuthContext context) {
        // Forwards the user's own bearer token — BFlow's API decides
        // resource authorization exactly as it does for the web client.
        // No domain code runs inside bflow-mcp.
        return bflowApiClient.post(
                "/api/v1/budgets",
                request,
                context.bearerToken(),
                BudgetResponse.class
        );
    }
}
```

`RequiresScope` is checked by a single interceptor (`McpScopeInterceptor`)
inside `bflow-mcp`, in front of the tool dispatcher. It never touches
`SecurityConfig` or anything inside the BFlow API — it's a local, additive
gate that decides whether `bflow-mcp` is even allowed to attempt the call.
The "prevent MCP from bypassing domain validation" requirement is
structural here, not just enforced by convention: `bflow-mcp` has no
dependency on the domain module at all, so there is nothing in it capable
of bypassing anything.

### 5. Token issuance (open question, flagged as still open)

Cognito remains the only Authorization Server. `bflow-mcp` expects a
Cognito-issued JWT carrying a `scope` claim, populated during an OAuth
authorization-code + consent step where the user picks which scopes to
grant their agent (this is what the MCP Authorization spec expects from
any remote MCP server). **I have not verified against your Cognito app
client configuration whether custom scopes or a token-exchange step are
already set up** — that's infrastructure work for issue #7
(Authentication), not this ADR.

### 6. Shared wallets

Scopes are per-agent-grant and apply across all wallets the user can see.
Role still comes from `WalletUser.role` per wallet, exactly as today:

```
Owner            → scope-permitted read/write operations: allowed
Authorized Member (WalletRole.MEMBER) → allowed only for the subset
                                          the shared-wallet role already
                                          permits in ServiceWalletSharing
Non-member       → AccessDeniedException, same as any web request
```

The agent never gains a permission the human user wouldn't have through
the web app for that specific wallet.

### 7. Initial permission matrix

| Capability                 | Scope               | v1 Status |
|-----------------------------|----------------------|-----------|
| View wallets                | `wallets:read`       | Allowed |
| View transactions           | `transactions:read`  | Allowed |
| Create/update transactions  | `transactions:write` | Allowed |
| View budgets                | `budgets:read`       | Allowed |
| Create/update budgets       | `budgets:write`      | Allowed |
| View recurring items        | `recurring:read`     | Allowed |
| Create/update recurring     | `recurring:write`    | Allowed |
| Money transfers              | N/A                  | Out of scope |
| Administrative operations    | N/A                  | Out of scope |

### 8. Future scopes

New scopes (e.g. `transfers:write` once designed) are additive: the
interceptor checks membership in the `scope` claim, so adding a scope
never changes behavior for tokens that don't request it, and no existing
client breaks.

## Consequences

### Positive

- Zero duplicated authorization logic: MCP inherits every `WalletRole`
  check `ServiceWallet`/`ServiceWalletSharing` already enforce, for free,
  by calling the same public API.
- The bypass guarantee is structural, not conventional: `bflow-mcp` has no
  dependency on the domain module, the repositories, or the database — it
  cannot reach them even by mistake.
- BFlow's main app deploys, scales, and fails independently of MCP; a bug
  or outage in `bflow-mcp` cannot take down the core API.
- Adding a scope or a tool later is additive, not a redesign.

### Negative / open work

- Token issuance and scope-consent UX (§5) still need a concrete Cognito
  design — tracked as issue #7 (Authentication), not covered here.
- `McpScopeInterceptor`, `@RequiresScope`, and `BflowApiClient` don't exist
  yet; this ADR defines the contract they must satisfy, not their
  implementation (tracked as issue #6, MCP Server).
- Every MCP write now costs one extra network hop (`bflow-mcp` → BFlow
  API) versus an in-process call. Acceptable for a chat-driven agent
  workflow, worth remembering if a future tool needs tight latency.
- High-risk operations (transfers, payments) will eventually need a
  step-up/confirmation model this ADR deliberately defers.
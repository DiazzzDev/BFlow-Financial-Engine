# API error contract for frontend clients

This document describes the error envelope returned by the BFlow API. HTTP status remains authoritative; clients should use the stable top-level `code` and field-level `errors[].code` for branching, and display localized `message` text to people.

## Validation errors

Invalid request bodies and validated parameters return HTTP `400` with `code: VALIDATION_ERROR`. Each error identifies the request property, a stable validation code, and a localized message. The frontend should bind by `field`, not parse the human-readable message.

```json
{
  "success": false,
  "message": "date: La fecha no puede ser futura.",
  "timestamp": "2026-10-30T03:34:45.065Z",
  "path": "/api/v1/incomes",
  "code": "VALIDATION_ERROR",
  "errors": [
    {
      "field": "date",
      "code": "transaction.date.futureNotAllowed",
      "message": "La fecha no puede ser futura."
    }
  ]
}
```

`field` is the DTO property path (nested properties may use dotted paths). `errors` can contain multiple entries for one or more fields. `code` within an entry is the validation-message key/code emitted by the validator; it is separate from the top-level `ErrorCode` enum. Keep unknown fields and codes forward-compatible.

## Common envelope

| Property | Meaning |
| --- | --- |
| `success` | `false` for errors. |
| `message` | Human-readable summary, localized where messages are configured. |
| `data` | Omitted for errors. |
| `timestamp` | Server-generated ISO-8601 instant. |
| `path` | Request path that failed. |
| `code` | Stable machine-readable error category; may be omitted on legacy error paths. |
| `errors` | Optional list of field-level validation failures. |

`errors` and `code` are omitted when not set. Do not assume every error has `errors`; domain errors generally have a top-level `code` and `message`. Some legacy paths may omit `code` until migrated, so status and message remain available as fallback presentation data.

## Suggested client handling

1. Branch on HTTP status and top-level `code` for behavior.
2. For `VALIDATION_ERROR`, map each `errors[]` item to the form control named by `field` and display `message`.
3. For other errors, display the top-level `message` and retain the request `path` for diagnostics.
4. Never branch on translated message text or assume the list contains only one error.
5. Treat unrecognized error codes as a generic request failure and preserve the response for diagnostics.

## Error status and code families

| HTTP status | Typical top-level codes | Frontend behavior |
| --- | --- | --- |
| 400 | `VALIDATION_ERROR`, `BAD_REQUEST`, `INVALID_TRANSACTION_DATE`, `INVALID_BUDGET_DATE`, `INVALID_FILE` | Correct the submitted values or file; field details are present when available. |
| 401 | `UNAUTHORIZED`, `INVALID_CREDENTIALS` | Refresh/re-authenticate or show sign-in feedback. |
| 403 | `FORBIDDEN`, `WALLET_ACCESS_DENIED`, `FILE_ACCESS_DENIED` | Explain that the resource is unavailable to this account. |
| 404 | `RESOURCE_NOT_FOUND`, `WALLET_NOT_FOUND`, `BUDGET_NOT_FOUND` | Show a missing/removed-resource state. |
| 409 | `CONFLICT`, `BUDGET_OVERLAP`, `IDEMPOTENCY_CONFLICT`, `PLAN_LIMIT_EXCEEDED` | Refresh state or guide the user to resolve the conflict/plan limit. |
| 5xx | `INTERNAL_SERVER_ERROR`, `STORAGE_ERROR` | Show a retryable failure without exposing internal details. |

The list is representative, not exhaustive. The OpenAPI `ErrorCode` schema is the source of currently declared top-level codes; endpoint descriptions define which statuses apply to each operation.

## OpenAPI / Swagger

Use the generated OpenAPI document (`/v3/api-docs`) as the machine-readable endpoint reference. Validation request constraints are documented from DTO schemas. Error responses use the `ApiResponse` envelope; field detail uses `FieldErrorResponse`.

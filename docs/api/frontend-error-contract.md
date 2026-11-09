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

## Where field errors come from

The API creates field errors in two ways:

1. Bean Validation on request DTOs (`@NotNull`, `@Size`, `@Pattern`, `@DecimalMin`, custom constraints, etc.), handled by `GlobalExceptionHandler.handleValidation`. The `field` is the DTO property name and the `code` comes from the constraint's message template. For message bundles, that is the key inside braces, such as `{transaction.title.required}`; some older DTOs use literal English message text as the code.
2. Domain validation promoted to a field error by a dedicated exception handler. For example, `InvalidTransactionDateException` maps to field `date` and top-level code `INVALID_TRANSACTION_DATE`.

Therefore the set is not a closed enum: it depends on which request DTO and validation rule an endpoint uses. Do not hard-code only `date`, and do not assume every field error code is an `ErrorCode` enum value. The endpoint's request schema in OpenAPI identifies its fields and constraints; the applicable DTO in the codebase is the source for exact validation rules and message keys.

### Common request-field groups

The following are recurring validation fields across modules. Exact applicability depends on the endpoint DTO:

| Field(s) | Validation source | Typical behavior |
| --- | --- | --- |
| `title`, `description` | `BaseTransactionRequest`, `RecurringRequest`, wallet/category DTOs | Required/length/allowed-character validation; codes use keys such as `transaction.title.required`, `transaction.title.size`, `transaction.title.invalidCharacters`. |
| `amount`, `initialValue` | Transaction, quick-expense, wallet, budget, receipt, transfer DTOs | Required, positive/range, decimal precision; keys include `transaction.amount.required`, `transaction.amount.tooSmall`, `transaction.amount.tooLarge`, `transaction.amount.invalidFormat`. |
| `date` | Transaction DTO/custom transaction-date validator; receipt confirmation | Required where transaction creation requires it, otherwise defaulted by the endpoint; rejects dates outside the business-date policy. Domain date failures use `INVALID_TRANSACTION_DATE`. |
| `walletId`, `fromWalletId`, `toWalletId` | Transaction/transfer DTOs | Required UUIDs; authorization or missing-resource failures may instead be top-level `WALLET_ACCESS_DENIED` or `WALLET_NOT_FOUND`. |
| `categoryId`, `type`, `frequency`, `period`, `scope`, `currency` | Transaction, recurring, budget DTOs | Required enum/identifier values; semantic incompatibilities can be domain errors rather than field-validation errors. |
| `startDate`, `endDate`, `intervalValue` | Budget and recurring DTOs | Required dates, bounded intervals, and cross-field order checks. Budget future-date errors use top-level `INVALID_BUDGET_DATE`; recurring end-before-start uses `recurring.endDate.beforeStart`. |
| `email`, `name`, `fileName`, `contentType`, `size`, `fileId` | Profile, invitation, storage, receipt DTOs | Format, requiredness, length, or positive size rules; file type/content checks can also return top-level `INVALID_FILE`. |

For a precise list for an endpoint, inspect its `@RequestBody` DTO and its validation annotations. A cross-field class-level constraint may report the DTO-level path (sometimes an empty field path), so clients should have a form-level error area in addition to per-control errors.

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

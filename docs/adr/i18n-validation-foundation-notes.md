# BFlow Backend — Validation, Error Handling & i18n Foundation

**Status:** Complete

## 1. Files created

- `common/response/ErrorCode.java`
- `common/response/FieldErrorResponse.java`
- `common/i18n/LocaleConfig.java`
- `common/i18n/MessageService.java`
- `common/i18n/package-info.java`
- `auth/enums/SupportedLanguage.java`
- `db/migration/V27__add_language_to_users.sql`
- `resources/messages.properties`
- `resources/messages_en.properties`
- `resources/messages_es.properties`
- `resources/templates/email/es/*.html` (6 files — translated from
  the existing English templates)
- `test/.../common/i18n/LocaleConfigTest.java`
- `test/.../common/i18n/LocaleEndToEndTest.java`
- `test/.../common/aws/service/EmailTemplateServiceFallbackTest.java`
- `test/resources/templates/email/en/only-english.html`

## 2. Files modified

- `common/response/ApiResponse.java` — additive `code`/`errors` fields
- `common/exception/GlobalExceptionHandler.java` — `ErrorCode` attached
  to ~30 of 36 handlers
- `category/DTO/CategoryRequest.java`
- `wallet/DTO/UpdateWalletRequest.java`
- `common/financial/BaseTransactionRequest.java`
- `expenses/DTO/ExpenseRequest.java`
- `income/DTO/IncomeRequest.java`
- `budget/DTO/BudgetRequest.java`
- `auth/DTO/user/UpdateUserProfileRequest.java`
- `auth/DTO/user/UserProfileResponse.java` — exposes `language`
- `auth/entities/User.java` — new `language` field
- `auth/services/UserService.java` — persists `language` on profile update
- `common/aws/service/EmailTemplateService.java` — locale-aware
  rendering + subjects
- `wallet/service/ServiceWalletSharing.java`
- `notifications/service/NotificationService.java`
- `recurring/services/RecurringTransactionExecutor.java`
- `recurring/services/RecurringExecutionService.java`
- `subscription/scheduler/SubscriptionRenewalScheduler.java`
- `resources/application.properties` — `spring.thymeleaf.prefix` updated
  to `classpath:/templates/email/`
- `resources/templates/*.html` → moved to `resources/templates/email/en/`
- `test/.../notifications/NotificationServiceTest.java` — updated mock
  signatures

## 3. Existing architecture reused

- **`ApiResponse`** kept as the one public error/success contract —
  no parallel `ApiError` shape was introduced, per the constraint to
  extend rather than replace.
- **18 pre-existing domain exceptions** (`BudgetNotFoundException`,
  `WalletAccessDeniedException`, `PlanLimitExceededException`,
  `StorageException`, etc.) were mapped to `ErrorCode` values rather
  than replaced with new exception types.
- **`GlobalExceptionHandler`** — extended in place; its existing
  Spanish-language user-facing strings (Wompi gateway errors, malformed
  request bodies) were preserved verbatim.
- **`Thymeleaf` + `EmailTemplateService`** — kept as the email
  rendering mechanism; no new templating engine or SES integration
  was introduced.
- **`User` entity's `PictureSource` pattern** — `language` follows the
  identical shape (`@Enumerated(STRING)`, `@Builder.Default`) rather
  than a new `UserPreferences` table, since no such table existed and
  the domain is currently small enough not to warrant one.

## 4. New API error contract

```json
{
  "success": false,
  "message": "Category not found.",
  "timestamp": "2026-09-09T00:00:00Z",
  "path": "/api/v1/categories/123",
  "code": "CATEGORY_NOT_FOUND"
}
```

Validation errors additionally carry `errors`:

```json
{
  "success": false,
  "message": "name: El nombre de la categoría es obligatorio.",
  "path": "/api/v1/categories",
  "code": "VALIDATION_ERROR",
  "errors": [
    { "field": "name", "code": "NotBlank",
      "message": "El nombre de la categoría es obligatorio." }
  ]
}
```

`code` and `errors` are `null`-omitted when absent — every pre-existing
`ApiResponse.success(...)` / two-argument `ApiResponse.error(...)` call
site produces byte-for-byte the same JSON as before this work.

## 5. Error codes added

26 total, defined in `ErrorCode.java`: 7 generic
(`VALIDATION_ERROR`, `BAD_REQUEST`, `UNAUTHORIZED`, `FORBIDDEN`,
`RESOURCE_NOT_FOUND`, `CONFLICT`, `INTERNAL_SERVER_ERROR`) plus 19
domain-specific ones, each mirroring an exception the application
already throws (see `GlobalExceptionHandler` for the full mapping).
Handlers for pure HTTP/framework mechanics with no clear domain
meaning (405, 406, 415, the Wompi gateway 502 fallback,
`IncorrectResultSizeDataAccessException`) deliberately have no code —
adding one would have meant inventing semantics that don't exist yet.

## 6. Validation improvements

Migrated to `{key}`-style i18n messages: `CategoryRequest`,
`UpdateWalletRequest`, `BaseTransactionRequest` (covers
`ExpenseRequest`/`IncomeRequest` via inheritance), `BudgetRequest`,
`UpdateUserProfileRequest`. `MethodArgumentNotValidException` handling
now returns per-field detail via `ApiResponse.validationError(...)`
in addition to the same joined-string `message` the frontend already
reads. Field-level `code` in that detail is presently the raw Bean
Validation constraint name (e.g. `NotBlank`) — see Remaining Technical
Debt.

## 7. i18n implementation

`messages.properties` / `messages_en.properties` /
`messages_es.properties`, resolved via a `ResourceBundleMessageSource`
bean (`fallbackToSystemLocale=false` — never silently depends on the
host JVM's locale). Same `MessageSource` bean is auto-wired by Spring
Boot into Bean Validation's message interpolator, so `{key}` message
placeholders in DTO annotations resolve through the same files.

Email subjects use the same infrastructure, resolved with an explicit
`Locale` (not `LocaleContextHolder`) since several email triggers run
outside an HTTP request thread.

## 8. Locale resolution strategy

- **API (validation/error messages):** `Accept-Language` header via
  `AcceptHeaderLocaleResolver`, restricted to `en`/`es`, default
  **Spanish** — matches BFlow's primary market. The current frontend
  sends no such header today, so this is forward-preparation with zero
  behavior change until the frontend adopts it.
- **Emails:** `User.language` takes priority, since the full `User`
  entity is already loaded at every send site — no extra query
  introduced. One exception: wallet invitations to an email that may
  not yet have an account use the app default (`ES`) rather than
  adding a lookup solely for this.

## 9. User preference changes

`SupportedLanguage { EN, ES }` enum; `User.language` field (default
`ES`), migration `V27`. Editable via `PATCH /api/v1/users/me`
(`UpdateUserProfileRequest.language`), readable via
`UserProfileResponse.language`.

## 10. Email template changes

All 6 user-facing templates (`budget-group-success`,
`email-verification`, `forgot-password`,
`recurring-transaction-failed`, `renewal-reminder`,
`wallet-invitation`) now exist in both `templates/email/en/` and
`templates/email/es/`, with identical Thymeleaf variable bindings
verified between language pairs. `contact-message` stays English-only
by design — it's addressed to BFlow's own support inbox, not
user-facing. Missing-translation fallback (`es` → `en`) is
implemented and covered by a dedicated test with a fixture template
that deliberately has no Spanish counterpart.

## 11. Backward compatibility considerations

- `ApiResponse.success`/two-arg `error` unchanged in signature and
  output.
- No existing endpoint's request/response shape changed.
- No existing validation annotation was removed — only had its
  hardcoded message text replaced with a `{key}` resolving to the
  *same* text by default (English fallback content matches what was
  previously hardcoded where applicable).
- Requests without `Accept-Language` (100% of the current frontend)
  continue to work, now receiving Spanish instead of an implicit
  system-locale-dependent language — a deliberate, expected change per
  this session's explicit direction, not a regression.

## 12. Tests added/modified

- `LocaleConfigTest` — message resolution in isolation (no Spring
  context).
- `LocaleEndToEndTest` — `Accept-Language` → validation message,
  exercised through the real `LocaleResolver` + `Validator` +
  `GlobalExceptionHandler`, via standalone MockMvc (no DB, no auth).
- `EmailTemplateServiceFallbackTest` — missing-translation fallback,
  against a real `SpringTemplateEngine` and a dedicated English-only
  fixture.
- `NotificationServiceTest` — updated for the new
  `sendBudgetGroupSuccessEmail` signature.

## 13. Remaining technical debt

- No user-facing UI/frontend changes were made or required by this
  work — `Accept-Language` support is inert until the frontend sends
  the header.
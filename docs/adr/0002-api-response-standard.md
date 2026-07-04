# ADR-0002: Standardize Controller Response Handling

- **Status:** Accepted
- **Date:** 2026-07-04

## Context

Controllers originally returned HTTP responses using `ResponseEntity` for nearly every endpoint, regardless of whether the HTTP status code was constant.

A typical implementation looked like:

```java
@PostMapping
public ResponseEntity<ApiResponse<ExpenseResponse>> create(
        @Valid @RequestBody final ExpenseRequest request) {

    ExpenseResponse created = serviceExpense.create(request);

    return ResponseEntity
            .status(HttpStatus.CREATED)
            .body(ApiResponse.success(
                    "Expense created successfully",
                    created,
                    "/api/v1/expenses"));
}
```

Although technically correct, this pattern introduced unnecessary boilerplate across the codebase.

Common issues included:

- Repeated `ResponseEntity.ok(...)` wrappers.
- Repeated `ResponseEntity.status(...)` calls.
- Increased visual noise in controllers.
- Business logic mixed with HTTP response construction.
- Reduced readability when the HTTP status was always constant.

Most endpoints always returned the same status code regardless of runtime conditions.

## Decision

Controllers will use `@ResponseStatus` whenever the HTTP status code is known at compile time.

Methods will return the response body directly instead of wrapping it in `ResponseEntity`.

For endpoints that do not return content, controllers will return `void` together with the appropriate HTTP status.

Examples:

### Before

```java
@PostMapping
public ResponseEntity<ApiResponse<ExpenseResponse>> create(...) {

    return ResponseEntity.status(HttpStatus.CREATED)
            .body(ApiResponse.success(...));
}
```

### After

```java
@PostMapping
@ResponseStatus(HttpStatus.CREATED)
public ApiResponse<ExpenseResponse> create(...) {

    return ApiResponse.success(...);
}
```

Delete endpoints become:

```java
@DeleteMapping("/{id}")
@ResponseStatus(HttpStatus.NO_CONTENT)
public void delete(...) {
    service.delete(id);
}
```

`ResponseEntity` remains the preferred choice only when the HTTP status or headers must be determined dynamically during request processing.

Examples include:

- Conditional status codes (200 vs 201)
- Optional responses (200 vs 204)
- File downloads
- Redirects
- Streaming responses
- Custom HTTP headers
- Runtime-dependent responses

## Consequences

### Positive

- Controllers become significantly smaller.
- Less boilerplate.
- Improved readability.
- HTTP status becomes explicit through annotations.
- Better separation between business logic and HTTP concerns.
- Consistent controller style across all modules.

### Negative

- Developers must recognize when `ResponseEntity` is still required.
- Endpoints with dynamic responses cannot adopt this pattern.

## Implementation Notes

This decision does not modify the API contract.

JSON payloads remain unchanged through the existing `ApiResponse` wrapper.

Only the controller implementation style changes by replacing unnecessary `ResponseEntity` wrappers with `@ResponseStatus` where appropriate.
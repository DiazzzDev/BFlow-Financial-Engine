package bflow.common.response;

/**
 * Stable, machine-readable error codes for the API error contract.
 *
 * These values are part of the frontend integration surface and must
 * never change once shipped — renaming a constant here is a breaking
 * change for any client matching on {@code error.code}. Add new
 * constants freely; never repurpose or rename an existing one.
 *
 * Codes carry no translated text (see {@code messages_en/es.properties}
 * for user-facing strings) and map to existing domain exceptions —
 * this list mirrors what the application currently throws, not a
 * speculative catalog of every conceivable error.
 */
public enum ErrorCode {

    // Generic / framework-level
    VALIDATION_ERROR,
    BAD_REQUEST,
    UNAUTHORIZED,
    FORBIDDEN,
    RESOURCE_NOT_FOUND,
    CONFLICT,
    INTERNAL_SERVER_ERROR,

    // Auth
    INVALID_CREDENTIALS,

    // Budgets
    BUDGET_NOT_FOUND,
    BUDGET_OVERLAP,
    INVALID_BUDGET_DATE,
    INVALID_BUDGET_SCOPE,
    INVALID_BUDGET_THRESHOLD,

    // Wallets
    WALLET_NOT_FOUND,
    WALLET_ACCESS_DENIED,

    // Files / storage
    INVALID_FILE,
    INVALID_STORAGE_KEY,
    STORAGE_ERROR,
    FILE_ACCESS_DENIED,

    // Plans / subscriptions
    PLAN_LIMIT_EXCEEDED,

    // Idempotency
    IDEMPOTENCY_CONFLICT,

    // Email
    EMAIL_DELIVERY_FAILED,

    // Legal documents
    LEGAL_DOCUMENT_NOT_FOUND
}
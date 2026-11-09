# ADR 0012: Preserve recorded transactions when a wallet lacks funds

## Status

Accepted.

## Context

Transaction notifications from Gmail commonly arrive before the customer has
entered prior income, opening balance, or another financial event. Rejecting an
expense because the current balance is too low silently loses a real event and
makes automated imports unreliable. The same issue occurs when a customer
corrects the amount of a past income or expense.

## Decision

BFlow permits a wallet balance to become negative when recording, editing, or
reconciling an income or expense. A negative balance represents the amount that
is currently uncovered; it is not an error and it does not hide the transaction.

Source-wallet funds checks remain in place for transfers. A transfer is not a
new financial event: allowing it to overdraw one wallet would create money in a
second wallet and break cross-wallet reconciliation.

## Consequences

- Gmail imports and manual entry use the same financial rule.
- Every recognized transaction remains visible in history and contributes to
  reports, even if its wallet is temporarily negative.
- Product surfaces should present negative balances clearly as an outstanding
  amount, rather than treating them as a failed import.
- Existing historical data is not changed; this decision affects validation of
  new operations and edits.

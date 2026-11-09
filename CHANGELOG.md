# BFlow Changelog

All notable changes to BFlow are documented here.

## v1.6.1

This release improves recurring transaction management.

### Added

* **Wallet Recurrence Lookup** — List active and inactive recurring transactions for an authorized wallet, and retrieve an individual recurrence by ID with ownership checks.
* **Recurrence Editing** — Update recurrence details without posting another income or expense. Schedule changes recalculate the next occurrence while preserving the active or paused state.
* **Indefinite Recurrences** — Leave the end date empty for recurring income or expenses with no known end date, such as a salary under an open-ended contract.

---

## v1.6.0

This release adds optional Gmail transaction import with privacy-focused content limits.

### Added

* **Gmail Transaction Import** — Connect Gmail separately from Google sign-in with explicit read-only consent. The background integration detects transaction confirmations and registers the resulting income or expense in the selected wallet, then notifies the user.
* **Content-Limited Processing** — Limit imports to supported PDF, JPEG, or PNG attachments and transaction-like email text. For each message, select one source in priority order: compatible PDF, compatible image, then the email text only when no compatible attachment exists. Unsupported attachments do not create separate transactions.
* **Temporary Receipt Analysis** — Analyze supported PDF and image attachments asynchronously through the existing Textract workflow using private temporary storage, then remove the staged file after processing.
* **Gmail Data Protection** — Encrypt connection credentials, avoid retaining complete email bodies or Gmail receipt files, and retain only the transaction and technical processing metadata needed to operate the integration and prevent duplicate imports.
* **Personal Data Export** — Add an authenticated JSON export for account and financial data associated with the requesting user.

---

## v1.5.1

* **Architecture Diagram** — Published an interactive diagram to help users explore how BFlow is organized: [architecture.bflow-studio.com](https://architecture.bflow-studio.com/).

---

## v1.5.0

This release improves subscriptions, recurring transactions, receipts, and shared wallets.

### Added

* **Subscription Renewals** — Added renewal reminders and a grace period, making it easier to keep a subscription active. Annual renewals preserve the remaining time on the current plan.
* **Annual Recurring Transactions** — Recurring income and expenses can now be scheduled annually, with safeguards against duplicate processing.
* **Receipt Improvements** — Receipt uploads and text recognition are more consistent, reducing the need to enter receipt details manually.
* **Wallet Invitations** — Added a default-wallet experience and clearer notifications for wallet invitations.
* **Profile Picture Safety** — Uploaded profile pictures are checked before being stored.

### Improved

* **Subscription Payments** — Pro access is granted only after a payment is confirmed. Payment and renewal flows now provide clearer, more reliable checkouts.
* **Business Transaction Dates** — Transaction dates follow El Salvador's local calendar, and date imports accept common date formats.
* **Account Deletion** — Financial history remains available to the system where needed after an account is permanently deleted.

---

## v1.0.0

### Added

* **Push Notifications** — Added push notifications to keep users informed about relevant activity.
* **Yearly Budgets** — Budgets can now be planned and tracked across a full year.

---

## v0.9.0

### Added

* **Account Deletion Controls** — Users can cancel a scheduled deletion within 30 days. After that period, the account is permanently deleted while necessary financial references are preserved.
* **English and Spanish Support** — Added a foundation for using the application in English and Spanish.

---

## v0.6.0

This release focuses on collaboration, data integrity, and platform foundations: shared wallets get a richer, more informative experience, receipt handling moves to automated OCR processing, and the first building blocks of an event-driven architecture are in place.

### Added

* **Receipt OCR via Amazon Textract** — Camera-first receipt upload now runs through an asynchronous OCR pipeline (S3 + Textract + SQS/SNS) to extract line items automatically, instead of manual entry.
* **User Search for Collaborators** — Wallet owners can search for users by name or email when inviting collaborators, with matching results showing name, email, and profile picture, and their current status (invitable, already a member, or invitation pending).
* **Contributor Identity on Transactions** — Transaction history now surfaces the name, email, and profile picture of the member who made each transaction, shown only on wallets with more than one member. Transactions can also be filtered by one or more specific contributors.
* **Richer Wallet Invitations** — Pending invitation responses now include the wallet's name and the inviting user's name, email, and profile picture, so invitees see who invited them and to what wallet without an extra lookup.
* **Profile Picture Upload** — Users can upload and update their own profile picture; the image is stored privately and served back through the application, keeping the same `pictureUrl` contract regardless of whether the picture originated from Google Sign-In or a direct upload.
* **Event-Driven Foundations (SQS + SNS)** — Introduced a reusable polling-worker abstraction for SQS consumers, currently powering invoice/receipt detection through the OCR pipeline. This lays the groundwork for a broader event-driven architecture planned for future automation ("autopilot") features.

### Fixed

* **Safe Wallet Deletion** — Deleting a wallet now checks for existing financial history — including recurring transactions — before allowing a permanent delete, preventing orphaned or corrupted financial data.
* **Pro Plan Seeding Conflicts** — Resolved a startup failure caused by duplicate/legacy `plans` rows conflicting with the Pro plan migration, and made the underlying migration idempotent so it can safely run across environments.
* **Category Seeding on Startup** — Replaced the in-code category seeder with a Flyway migration, removing an unnecessary synchronous JPA initialization cost from every application boot.

### Changed

* **Spring Boot 4.1.0 → 4.1.1** — Upgraded for upstream performance and stability improvements.
* Reduced unnecessary Hibernate dirty-checking overhead on read-only budget queries.
* Enabled JDBC batching for bulk inserts/updates, reducing database round-trips for operations like recurring transaction processing.

### Security

* Disabled Swagger UI and OpenAPI documentation endpoints in the production profile.

---

## v0.5.0

**First production release of BFlow Studio.**

This release introduces the core financial management features that make up the initial BFlow experience, including multi-wallet management, shared wallets, budgets, recurring transactions, automated processing, email notifications, and secure authentication.

### Added

* **Wallets** — Create and manage multiple wallets to organize different sources of money, including personal funds, savings, cash, and bank accounts.
* **Shared Wallets** — Share wallets with other people to manage finances together.
* **Budgets** — Create spending budgets, organize them by category, and track budget usage.
* **Recurring Transactions** — Configure recurring income and expenses such as rent, subscriptions, salaries, bills, and regular transfers.
* **Automated Recurring Processing** — Recurring transactions are automatically processed when they become due through background scheduled jobs.
* **Email Notifications** — Added email notifications for relevant account and financial activity.
* **Transaction Management** — Added transaction management with automatic updates to wallet balances.

### Security

* **Google Sign-In** — Added Google authentication through the configured identity provider.

### Changed

* Improved wallet balance consistency when transactions are created, updated, or removed.
* Improved budget calculations and tracking across financial activity.
* Improved the overall consistency of financial data across wallets, transactions, and budgets.
* Added background processing infrastructure for automated financial operations.

---

## What's Next

Future releases will focus on improving financial insights, automation, integrations, and the overall BFlow Studio experience.

BFlow is continuously evolving, and user feedback will help guide future development.

---

## Version

**BFlow v1.6.1**

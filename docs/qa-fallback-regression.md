# QA fallback regression report

## Production change

`NotificationService` previously persisted and published an in-app notification, then called SES. An SES outage raised `EmailDeliveryException` through the notification flow. A caller retry could repeat the in-app notification even though its primary work had already succeeded.

The service now catches only `EmailDeliveryException` around optional email delivery. Single-recipient notifications remain available in-app when SES is unavailable, and a group email failure does not stop notifications or email attempts for the remaining members. Persistence and unrelated application errors still propagate.

This is the only production change in this batch. It is kept in its own commit, separate from the test commit.

## Regression coverage added

- Personal budget notification remains saved when SES delivery fails.
- A group email failure does not interrupt processing other members.
- SES failures are wrapped in `EmailDeliveryException` at the SES adapter.
- FCM delivery without configuration is a no-op; runtime errors are isolated per token; unregistered tokens are disabled, while transient errors leave tokens enabled.
- Textract failures for failed jobs and non-receipt documents transition to failed; transient Textract errors propagate so SQS can redeliver; duplicate completion events are ignored.
- Storage cleanup failures are attached as suppressed exceptions while preserving the original moderation error.
- Image signature and parent-label moderation rejections are covered.
- Textract confidence expectation is `89.33`, averaging the three detected field confidences: 90, 98, and 80.

## Validation status

Tests were not run locally, per the request to rely on the standardized environment and CI. No coverage exclusions or thresholds were changed. The pipeline must confirm compilation, test results, JaCoCo coverage, and PIT mutation score.

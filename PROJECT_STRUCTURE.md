# BFlow Backend

```text
|-- .devcontainer
|   |-- devcontainer.json
|   |-- docker-compose.yml
|   `-- Dockerfile
|-- .dockerignore
|-- .env.example
|-- .gitattributes
|-- .github
|   |-- appmod
|   |-- dependabot.yml
|   |-- java-upgrade
|   |-- modernize
|   `-- workflows
|       |-- ci.yml
|       |-- deploy.yml
|       `-- security.yml
|-- .gitignore
|-- .mvn
|   `-- wrapper
|       `-- maven-wrapper.properties
|-- .trivyignore
|-- CODE_OF_CONDUCT.md
|-- CONTRIBUTING.md
|-- docker-compose.yml
|-- Dockerfile
|-- docs
|   |-- adr
|   |   |-- 0001-migrate-to-aws-cognito.md
|   |   |-- 0002-api-response-standard.md
|   |   |-- 0003-enforce-idempotency-for-financial-operations.md
|   |   |-- 0004-multi-stage-pipeline.md
|   |   |-- 0005-portable-and-idempotent-infrastructure-provisioning.md
|   |   `-- 0006-ecs-fargate-deployment-with-cloudflare-dynamic-dns.md
|   `-- setup.md
|-- ecs
|   |-- network-config.template.json
|   `-- task-definition.template.json
|-- infra
|   |-- bootstrap
|   |   |-- 01-vpc.sh
|   |   |-- 02-subnets.sh
|   |   |-- 03-internet-gateway.sh
|   |   |-- 04-route-tables.sh
|   |   |-- 05-security-groups.sh
|   |   |-- 06-ecr.sh
|   |   |-- 07-rds.sh
|   |   |-- 08-secrets.sh
|   |   |-- 09-iam.sh
|   |   |-- 10-cloudwatch.sh
|   |   |-- 11-ecs.sh
|   |   |-- 12-github-oidc.sh
|   |   `-- 13-budget.sh
|   |-- config.env.example
|   |-- deploy.sh
|   |-- destroy
|   |   |-- 01-vpc.sh
|   |   |-- 02-subnets.sh
|   |   |-- 03-internet-gateway.sh
|   |   |-- 04-route-tables.sh
|   |   |-- 05-security-groups.sh
|   |   |-- 06-ecr.sh
|   |   |-- 07-rds.sh
|   |   |-- 08-secrets.sh
|   |   |-- 09-iam.sh
|   |   |-- 10-cloudwatch.sh
|   |   |-- 11-ecs.sh
|   |   |-- 12-github-oidc.sh
|   |   `-- 13-budget.sh
|   |-- destroy.sh
|   |-- emergency-cleanup.sh
|   |-- lib
|   |   |-- ecr-policy.json
|   |   `-- helpers.sh
|   |-- outputs.env.example
|   |-- README.md
|-- LICENSE
|-- mvnw
|-- mvnw.cmd
|-- pom.xml
|-- PROJECT_STRUCTURE.md
|-- README.md
|-- src
|   |-- main
|   |   |-- java
|   |   |   `-- bflow
|   |   |       |-- BFlowApplication.java
|   |   |       |-- package-info.java
|   |   |       |-- ServletInitializer.java
|   |   |       |-- auth
|   |   |       |   |-- controllers
|   |   |       |   |   |-- AuthController.java
|   |   |       |   |   |-- package-info.java
|   |   |       |   |   `-- UserController.java
|   |   |       |   |-- DTO
|   |   |       |   |   |-- AuthMeResponse.java
|   |   |       |   |   |-- package-info.java
|   |   |       |   |   |-- UserMeResponse.java
|   |   |       |   |   |-- Record
|   |   |       |   |   |   |-- package-info.java
|   |   |       |   |   |   |-- SyncUserRequest.java
|   |   |       |   |   |   `-- SyncUserResponse.java
|   |   |       |   |   `-- user
|   |   |       |   |       |-- package-info.java
|   |   |       |   |       |-- UpdateUserProfileRequest.java
|   |   |       |   |       `-- UserProfileResponse.java
|   |   |       |   |-- entities
|   |   |       |   |   |-- AuthAccount.java
|   |   |       |   |   |-- package-info.java
|   |   |       |   |   `-- User.java
|   |   |       |   |-- enums
|   |   |       |   |   |-- AuthProvider.java
|   |   |       |   |   |-- package-info.java
|   |   |       |   |   `-- UserStatus.java
|   |   |       |   |-- repository
|   |   |       |   |   |-- package-info.java
|   |   |       |   |   |-- RepositoryAuthAccount.java
|   |   |       |   |   `-- RepositoryUser.java
|   |   |       |   |-- security
|   |   |       |   |   |-- CognitoIdTokenValidator.java
|   |   |       |   |   |-- CognitoJwtConfig.java
|   |   |       |   |   |-- CorsConfig.java
|   |   |       |   |   |-- PasswordEncoderConfig.java
|   |   |       |   |   |-- package-info.java
|   |   |       |   |   `-- SecurityConfig.java
|   |   |       |   `-- services
|   |   |       |       |-- AuthBootstrapService.java
|   |   |       |       |-- AuthService.java
|   |   |       |       |-- AuthSyncService.java
|   |   |       |       |-- CurrentUserService.java
|   |   |       |       |-- package-info.java
|   |   |       |       |-- UserService.java
|   |   |       |       `-- UserServiceImpl.java
|   |   |       |-- budget
|   |   |       |   |-- ControllerBudget.java
|   |   |       |   |-- DTO
|   |   |       |   |   |-- BudgetPatchRequest.java
|   |   |       |   |   |-- BudgetRequest.java
|   |   |       |   |   |-- BudgetResponse.java
|   |   |       |   |   |-- BudgetSummaryResponse.java
|   |   |       |   |   `-- package-info.java
|   |   |       |   |-- entity
|   |   |       |   |   |-- Budget.java
|   |   |       |   |   `-- package-info.java
|   |   |       |   |-- enums
|   |   |       |   |   |-- BudgetScope.java
|   |   |       |   |   |-- BudgetStatus.java
|   |   |       |   |   |-- package-info.java
|   |   |       |   |   `-- PeriodType.java
|   |   |       |   |-- package-info.java
|   |   |       |   |-- RepositoryBudget.java
|   |   |       |   `-- services
|   |   |       |       |-- BudgetAlertService.java
|   |   |       |       |-- BudgetCalculationService.java
|   |   |       |       |-- BudgetLifecycleService.java
|   |   |       |       |-- BudgetOverlapValidationService.java
|   |   |       |       |-- BudgetService.java
|   |   |       |       |-- BudgetValidationService.java
|   |   |       |       `-- package-info.java
|   |   |       |-- category
|   |   |       |   |-- CategorySeeder.java
|   |   |       |   |-- CategoryValidator.java
|   |   |       |   |-- ControllerCategory.java
|   |   |       |   |-- DTO
|   |   |       |   |   |-- CategoryRequest.java
|   |   |       |   |   |-- CategoryResponse.java
|   |   |       |   |   `-- package-info.java
|   |   |       |   |-- entity
|   |   |       |   |   |-- Category.java
|   |   |       |   |   `-- package-info.java
|   |   |       |   |-- enums
|   |   |       |   |   |-- CategoryType.java
|   |   |       |   |   `-- package-info.java
|   |   |       |   |-- package-info.java
|   |   |       |   |-- RepositoryCategory.java
|   |   |       |   `-- ServiceCategory.java
|   |   |       |-- common
|   |   |       |   |-- aws
|   |   |       |   |   |-- config
|   |   |       |   |   |   |-- AwsSesConfig.java
|   |   |       |   |   |   `-- package-info.java
|   |   |       |   |   `-- service
|   |   |       |   |       |-- EmailTemplateService.java
|   |   |       |   |       |-- package-info.java
|   |   |       |   |       `-- SesEmailService.java
|   |   |       |   |-- config
|   |   |       |   |   |-- JacksonConfig.java
|   |   |       |   |   `-- package-info.java
|   |   |       |   |-- exception
|   |   |       |   |   |-- BudgetNotFoundException.java
|   |   |       |   |   |-- BudgetOverlapException.java
|   |   |       |   |   |-- EmailDeliveryException.java
|   |   |       |   |   |-- GlobalExceptionHandler.java
|   |   |       |   |   |-- InvalidBudgetDateException.java
|   |   |       |   |   |-- InvalidBudgetScopeException.java
|   |   |       |   |   |-- InvalidBudgetThresholdException.java
|   |   |       |   |   |-- InvalidCredentialsException.java
|   |   |       |   |   |-- NotFoundException.java
|   |   |       |   |   |-- package-info.java
|   |   |       |   |   |-- ResourceNotFoundException.java
|   |   |       |   |   `-- WalletAccessDeniedException.java
|   |   |       |   |-- financial
|   |   |       |   |   |-- BaseTransactionRequest.java
|   |   |       |   |   |-- package-info.java
|   |   |       |   |   |-- Transaction.java
|   |   |       |   |   `-- TransactionMapper.java
|   |   |       |   |-- idempotency
|   |   |       |   |   |-- config
|   |   |       |   |   |   |-- IdempotencyProperties.java
|   |   |       |   |   |   `-- package-info.java
|   |   |       |   |   |-- entity
|   |   |       |   |   |   |-- IdempotencyRecord.java
|   |   |       |   |   |   `-- package-info.java
|   |   |       |   |   |-- exception
|   |   |       |   |   |   |-- IdempotencyConflictException.java
|   |   |       |   |   |   `-- package-info.java
|   |   |       |   |   |-- filter
|   |   |       |   |   |   |-- CachedBodyHttpServletRequest.java
|   |   |       |   |   |   |-- IdempotencyFilter.java
|   |   |       |   |   |   `-- package-info.java
|   |   |       |   |   |-- package-info.java
|   |   |       |   |   |-- repository
|   |   |       |   |   |   |-- package-info.java
|   |   |       |   |   |   `-- RepositoryIdempotency.java
|   |   |       |   |   |-- scheduler
|   |   |       |   |   |   |-- IdempotencyCleanupTask.java
|   |   |       |   |   |   `-- package-info.java
|   |   |       |   |   `-- service
|   |   |       |   |       |-- IdempotencyService.java
|   |   |       |   |       `-- package-info.java
|   |   |       |   |-- response
|   |   |       |   |   |-- ApiResponse.java
|   |   |       |   |   `-- package-info.java
|   |   |       |-- expenses
|   |   |       |   |-- controllers
|   |   |       |   |   |-- ControllerExpense.java
|   |   |       |   |   |-- package-info.java
|   |   |       |   |   `-- QuickExpenseController.java
|   |   |       |   |-- DTO
|   |   |       |   |   |-- ExpenseRequest.java
|   |   |       |   |   |-- ExpenseResponse.java
|   |   |       |   |   |-- package-info.java
|   |   |       |   |   `-- QuickExpenseRequest.java
|   |   |       |   |-- entity
|   |   |       |   |   |-- Expense.java
|   |   |       |   |   `-- package-info.java
|   |   |       |   |-- enums
|   |   |       |   |   |-- ExpenseType.java
|   |   |       |   |   `-- package-info.java
|   |   |       |   |-- package-info.java
|   |   |       |   |-- RepositoryExpense.java
|   |   |       |   `-- services
|   |   |       |       |-- package-info.java
|   |   |       |       |-- QuickExpenseService.java
|   |   |       |       `-- ServiceExpense.java
|   |   |       |-- income
|   |   |       |   |-- ControllerIncome.java
|   |   |       |   |-- DTO
|   |   |       |   |   |-- IncomeRequest.java
|   |   |       |   |   |-- IncomeResponse.java
|   |   |       |   |   `-- package-info.java
|   |   |       |   |-- entity
|   |   |       |   |   |-- Income.java
|   |   |       |   |   `-- package-info.java
|   |   |       |   |-- enums
|   |   |       |   |   |-- IncomeType.java
|   |   |       |   |   `-- package-info.java
|   |   |       |   |-- package-info.java
|   |   |       |   |-- RepositoryIncome.java
|   |   |       |   `-- ServiceIncome.java
|   |   |       |-- legal
|   |   |       |   |-- controller
|   |   |       |   |   |-- LegalController.java
|   |   |       |   |   `-- package-info.java
|   |   |       |   |-- dto
|   |   |       |   |   |-- LegalDocumentResponse.java
|   |   |       |   |   `-- package-info.java
|   |   |       |   |-- enums
|   |   |       |   |   |-- LegalDocumentType.java
|   |   |       |   |   `-- package-info.java
|   |   |       |   |-- exception
|   |   |       |   |   |-- LegalDocumentNotFoundException.java
|   |   |       |   |   `-- package-info.java
|   |   |       |   |-- service
|   |   |       |   |   |-- LegalService.java
|   |   |       |   |   |-- LegalServiceImpl.java
|   |   |       |   |   `-- package-info.java
|   |   |       |-- merchant
|   |   |       |   |-- MerchantDetectionService.java
|   |   |       |   |-- MerchantPattern.java
|   |   |       |   |-- MerchantPatternRepository.java
|   |   |       |   `-- package-info.java
|   |   |       |-- notifications
|   |   |       |   |-- ControllerNotification.java
|   |   |       |   |-- DTO
|   |   |       |   |   |-- NotificationResponse.java
|   |   |       |   |   `-- package-info.java
|   |   |       |   |-- entity
|   |   |       |   |   |-- Notification.java
|   |   |       |   |   `-- package-info.java
|   |   |       |   |-- enums
|   |   |       |   |   |-- NotificationType.java
|   |   |       |   |   `-- package-info.java
|   |   |       |   |-- package-info.java
|   |   |       |   |-- repository
|   |   |       |   |   |-- NotificationRepository.java
|   |   |       |   |   `-- package-info.java
|   |   |       |   `-- service
|   |   |       |       |-- NotificationService.java
|   |   |       |       `-- package-info.java
|   |   |       |-- rate_limit
|   |   |       |   |-- config
|   |   |       |   |   |-- RateLimitConfiguration.java
|   |   |       |   |   |-- RateLimitProperties.java
|   |   |       |   |   `-- package-info.java
|   |   |       |   |-- dto
|   |   |       |   |   |-- RateLimitErrorResponse.java
|   |   |       |   |   `-- package-info.java
|   |   |       |   |-- filter
|   |   |       |   |   |-- RateLimitFilter.java
|   |   |       |   |   `-- package-info.java
|   |   |       |   |-- policy
|   |   |       |   |   |-- EndpointPolicyResolver.java
|   |   |       |   |   |-- RateLimitPolicy.java
|   |   |       |   |   |-- RateLimitPolicyRegistry.java
|   |   |       |   |   `-- package-info.java
|   |   |       |   |-- scheduler
|   |   |       |   |   |-- RateLimitCleanupTask.java
|   |   |       |   |   `-- package-info.java
|   |   |       |   |-- service
|   |   |       |   |   |-- BucketStorageService.java
|   |   |       |   |   |-- InMemoryBucketStorageService.java
|   |   |       |   |   |-- RateLimitService.java
|   |   |       |   |   `-- package-info.java
|   |   |       |   |-- strategy
|   |   |       |   |   |-- IpKeyResolver.java
|   |   |       |   |   |-- KeyResolver.java
|   |   |       |   |   |-- UserKeyResolver.java
|   |   |       |   |   `-- package-info.java
|   |   |       |   |-- util
|   |   |       |   |   |-- ClientIpUtil.java
|   |   |       |   |   |-- RateLimitResponseUtil.java
|   |   |       |   |   `-- package-info.java
|   |   |       |-- recurring
|   |   |       |   |-- ControllerRecurring.java
|   |   |       |   |-- DTO
|   |   |       |   |   |-- RecurringRequest.java
|   |   |       |   |   |-- RecurringResponse.java
|   |   |       |   |   `-- package-info.java
|   |   |       |   |-- entity
|   |   |       |   |   |-- RecurringTransaction.java
|   |   |       |   |   `-- package-info.java
|   |   |       |   |-- enums
|   |   |       |   |   |-- RecurringFrequency.java
|   |   |       |   |   |-- RecurringType.java
|   |   |       |   |   `-- package-info.java
|   |   |       |   |-- package-info.java
|   |   |       |   |-- RepositoryRecurringTransaction.java
|   |   |       |   `-- services
|   |   |       |       |-- RecurringExecutionService.java
|   |   |       |       |-- RecurringScheduler.java
|   |   |       |       `-- package-info.java
|   |   |       |-- subscription
|   |   |       |   |-- controllers
|   |   |       |   |   |-- SubscriptionController.java
|   |   |       |   |   `-- package-info.java
|   |   |       |   |-- dto
|   |   |       |   |   |-- CurrentSubscriptionResponse.java
|   |   |       |   |   |-- PlanResponse.java
|   |   |       |   |   |-- SubscriptionResponse.java
|   |   |       |   |   `-- package-info.java
|   |   |       |   |-- entities
|   |   |       |   |   |-- Payment.java
|   |   |       |   |   |-- Plan.java
|   |   |       |   |   |-- Subscription.java
|   |   |       |   |   `-- package-info.java
|   |   |       |   |-- enums
|   |   |       |   |   |-- BillingPeriod.java
|   |   |       |   |   |-- PaymentStatus.java
|   |   |       |   |   |-- SubscriptionStatus.java
|   |   |       |   |   `-- package-info.java
|   |   |       |   |-- package-info.java
|   |   |       |   |-- PlanCodes.java
|   |   |       |   |-- repository
|   |   |       |   |   |-- RepositoryPayment.java
|   |   |       |   |   |-- RepositoryPlan.java
|   |   |       |   |   |-- RepositorySubscription.java
|   |   |       |   |   `-- package-info.java
|   |   |       |   |-- scheduler
|   |   |       |   |   |-- SubscriptionRenewalScheduler.java
|   |   |       |   |   `-- package-info.java
|   |   |       |   `-- services
|   |   |       |       |-- PaymentService.java
|   |   |       |       |-- PlanService.java
|   |   |       |       |-- SubscriptionService.java
|   |   |       |       |-- SubscriptionValidationService.java
|   |   |       |       `-- package-info.java
|   |   |       |-- tranfers
|   |   |       |   |-- ControllerTransfer.java
|   |   |       |   |-- DTO
|   |   |       |   |   |-- TransferenceRequest.java
|   |   |       |   |   |-- TransferenceResponse.java
|   |   |       |   |   `-- package-info.java
|   |   |       |   |-- entities
|   |   |       |   |   |-- Transfer.java
|   |   |       |   |   `-- package-info.java
|   |   |       |   |-- enums
|   |   |       |   |   |-- TransferStatus.java
|   |   |       |   |   `-- package-info.java
|   |   |       |   |-- package-info.java
|   |   |       |   |-- RepositoryTransfers.java
|   |   |       |   `-- ServiceTransfers.java
|   |   |       `-- wallet
|   |   |           |-- ControllerWallet.java
|   |   |           |-- DTO
|   |   |           |   |-- package-info.java
|   |   |           |   |-- UpdateWalletRequest.java
|   |   |           |   |-- UserWalletsResponse.java
|   |   |           |   |-- WalletPair.java
|   |   |           |   |-- WalletRequest.java
|   |   |           |   `-- WalletResponse.java
|   |   |           |-- entities
|   |   |           |   |-- package-info.java
|   |   |           |   |-- Wallet.java
|   |   |           |   `-- WalletUser.java
|   |   |           |-- enums
|   |   |           |   |-- Currency.java
|   |   |           |   |-- package-info.java
|   |   |           |   |-- WalletRole.java
|   |   |           |   `-- WalletType.java
|   |   |           |-- package-info.java
|   |   |           |-- RepositoryWallet.java
|   |   |           |-- RepositoryWalletUser.java
|   |   |           |-- ServiceWallet.java
|   |   |           `-- WalletLockService.java
|   |   `-- resources
|   |       |-- application.properties
|   |       |-- db
|   |       |   `-- migration
|   |       |       |-- V1__baseline.sql
|   |       |       |-- V2__seed_plans.sql
|   |       |       `-- V3__seed_categories.sql
|   |       |-- legal
|   |       |   |-- cookies_en.md
|   |       |   |-- cookies_es.md
|   |       |   |-- privacy_en.md
|   |       |   |-- privacy_es.md
|   |       |   |-- terms_en.md
|   |       |   `-- terms_es.md
|   |       `-- templates
|   |           |-- email-verification.html
|   |           `-- forgot-password.html
|   `-- test
|       |-- java
|       |   `-- Diaz
|       |       `-- Dev
|       |           `-- BFlow
|       |               |-- BFlowApplicationTests.java
|       |               |-- common
|       |               |   |-- exception
|       |               |   |   `-- InvalidCredentialsExceptionTest.java
|       |               |   `-- response
|       |               |       `-- ApiResponseTest.java
|       |               |-- expenses
|       |               |   `-- ServiceExpenseTest.java
|       |               |-- income
|       |               |   `-- ServiceIncomeTest.java
|       |               |-- tranfers
|       |               |   `-- ServiceTransfersTest.java
|       |               `-- wallet
|       |                   |-- entities
|       |                   `-- ServiceWalletTest.java
|       `-- resources
|           `-- application-test.properties
```

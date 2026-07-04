# BFlow Backend

```
|-- .devcontainer
|   |-- devcontainer.json
|   |-- docker-compose.yml
|   `-- Dockerfile
|-- .dockerignore
|-- .env.example
|-- .gitattributes
|-- .github
|   |-- dependabot.yml
|   `-- workflows
|       |-- gitleaks.yml
|       `-- github-pipeline.yml
|-- .gitignore
|-- .mvn
|   `-- wrapper
|       `-- maven-wrapper.properties
|-- CODE_OF_CONDUCT.md
|-- CONTRIBUTING.md
|-- docker-compose.yml
|-- Dockerfile
|-- docs
|   `-- setup.md
|-- LICENSE
|-- mvnw
|-- mvnw.cmd
|-- pom.xml
|-- PROJECT_STRUCTURE.md
|-- README.md
`-- src
    |-- main
    |   |-- java
    |   |   `-- bflow
    |   |       |-- auth
    |   |       |   |-- controllers
    |   |       |   |   |-- AuthController.java
    |   |       |   |   |-- package-info.java
    |   |       |   |   `-- UserController.java
    |   |       |   |-- DTO
    |   |       |   |   |-- AuthMeResponse.java
    |   |       |   |   |-- package-info.java
    |   |       |   |   |-- Record
    |   |       |   |   |   |-- package-info.java
    |   |       |   |   |   |-- SyncUserRequest.java
    |   |       |   |   |   `-- SyncUserResponse.java
    |   |       |   |   |-- user
    |   |       |   |   |   |-- package-info.java
    |   |       |   |   |   |-- UpdateUserProfileRequest.java
    |   |       |   |   |   `-- UserProfileResponse.java
    |   |       |   |   `-- UserMeResponse.java
    |   |       |   |-- entities
    |   |       |   |   |-- AuthAccount.java
    |   |       |   |   |-- package-info.java
    |   |       |   |   |-- Role.java
    |   |       |   |   `-- User.java
    |   |       |   |-- enums
    |   |       |   |   |-- AuthProvider.java
    |   |       |   |   |-- package-info.java
    |   |       |   |   `-- UserStatus.java
    |   |       |   |-- repository
    |   |       |   |   |-- package-info.java
    |   |       |   |   |-- RepositoryAuthAccount.java
    |   |       |   |   |-- RepositoryRole.java
    |   |       |   |   `-- RepositoryUser.java
    |   |       |   |-- security
    |   |       |   |   |-- CognitoIdTokenValidator.java
    |   |       |   |   |-- CognitoJwtConfig.java
    |   |       |   |   |-- CorsConfig.java
    |   |       |   |   |-- CurrentUser.java
    |   |       |   |   |-- package-info.java
    |   |       |   |   |-- PasswordEncoderConfig.java
    |   |       |   |   `-- SecurityConfig.java
    |   |       |   `-- services
    |   |       |       |-- AuthBootstrapService.java
    |   |       |       |-- AuthService.java
    |   |       |       |-- AuthSyncService.java
    |   |       |       |-- CurrentUserService.java
    |   |       |       |-- package-info.java
    |   |       |       |-- UserService.java
    |   |       |       `-- UserServiceImpl.java
    |   |       |-- BFlowApplication.java
    |   |       |-- budget
    |   |       |   |-- ControllerBudget.java
    |   |       |   |-- DTO
    |   |       |   |   |-- BudgetPatchRequest.java
    |   |       |   |   |-- BudgetRequest.java
    |   |       |   |   |-- BudgetResponse.java
    |   |       |   |   |-- BudgetSummaryResponse.java
    |   |       |   |   `-- package-info.java
    |   |       |   |-- entity
    |   |       |   |   |-- Budget.java
    |   |       |   |   `-- package-info.java
    |   |       |   |-- enums
    |   |       |   |   |-- BudgetScope.java
    |   |       |   |   |-- BudgetStatus.java
    |   |       |   |   |-- package-info.java
    |   |       |   |   `-- PeriodType.java
    |   |       |   |-- package-info.java
    |   |       |   |-- RepositoryBudget.java
    |   |       |   `-- services
    |   |       |       |-- BudgetAlertService.java
    |   |       |       |-- BudgetCalculationService.java
    |   |       |       |-- BudgetLifecycleService.java
    |   |       |       |-- BudgetOverlapValidationService.java
    |   |       |       |-- BudgetService.java
    |   |       |       |-- BudgetValidationService.java
    |   |       |       `-- package-info.java
    |   |       |-- category
    |   |       |   |-- CategorySeeder.java
    |   |       |   |-- CategoryValidator.java
    |   |       |   |-- ControllerCategory.java
    |   |       |   |-- DTO
    |   |       |   |   |-- CategoryRequest.java
    |   |       |   |   |-- CategoryResponse.java
    |   |       |   |   `-- package-info.java
    |   |       |   |-- entity
    |   |       |   |   |-- Category.java
    |   |       |   |   `-- package-info.java
    |   |       |   |-- enums
    |   |       |   |   |-- CategoryType.java
    |   |       |   |   `-- package-info.java
    |   |       |   |-- package-info.java
    |   |       |   |-- RepositoryCategory.java
    |   |       |   `-- ServiceCategory.java
    |   |       |-- common
    |   |       |   |-- aws
    |   |       |   |   |-- config
    |   |       |   |   |   |-- AwsSesConfig.java
    |   |       |   |   |   `-- package-info.java
    |   |       |   |   `-- service
    |   |       |   |       |-- EmailTemplateService.java
    |   |       |   |       |-- package-info.java
    |   |       |   |       `-- SesEmailService.java
    |   |       |   |-- exception
    |   |       |   |   |-- ApiError.java
    |   |       |   |   |-- BudgetNotFoundException.java
    |   |       |   |   |-- BudgetOverlapException.java
    |   |       |   |   |-- EmailDeliveryException.java
    |   |       |   |   |-- GlobalExceptionHandler.java
    |   |       |   |   |-- InvalidBudgetDateException.java
    |   |       |   |   |-- InvalidBudgetScopeException.java
    |   |       |   |   |-- InvalidBudgetThresholdException.java
    |   |       |   |   |-- InvalidCredentialsException.java
    |   |       |   |   |-- NotFoundException.java
    |   |       |   |   |-- package-info.java
    |   |       |   |   |-- ResourceNotFoundException.java
    |   |       |   |   `-- WalletAccessDeniedException.java
    |   |       |   |-- financial
    |   |       |   |   |-- BaseTransactionRequest.java
    |   |       |   |   |-- package-info.java
    |   |       |   |   |-- Transaction.java
    |   |       |   |   `-- TransactionMapper.java
    |   |       |   `-- response
    |   |       |       |-- ApiResponse.java
    |   |       |       `-- package-info.java
    |   |       |-- expenses
    |   |       |   |-- controllers
    |   |       |   |   |-- ControllerExpense.java
    |   |       |   |   |-- package-info.java
    |   |       |   |   `-- QuickExpenseController.java
    |   |       |   |-- DTO
    |   |       |   |   |-- ExpenseRequest.java
    |   |       |   |   |-- ExpenseResponse.java
    |   |       |   |   |-- package-info.java
    |   |       |   |   `-- QuickExpenseRequest.java
    |   |       |   |-- entity
    |   |       |   |   |-- Expense.java
    |   |       |   |   `-- package-info.java
    |   |       |   |-- enums
    |   |       |   |   |-- ExpenseType.java
    |   |       |   |   `-- package-info.java
    |   |       |   |-- package-info.java
    |   |       |   |-- RepositoryExpense.java
    |   |       |   `-- services
    |   |       |       |-- package-info.java
    |   |       |       |-- QuickExpenseService.java
    |   |       |       `-- ServiceExpense.java
    |   |       |-- income
    |   |       |   |-- ControllerIncome.java
    |   |       |   |-- DTO
    |   |       |   |   |-- IncomeRequest.java
    |   |       |   |   |-- IncomeResponse.java
    |   |       |   |   `-- package-info.java
    |   |       |   |-- entity
    |   |       |   |   |-- Income.java
    |   |       |   |   `-- package-info.java
    |   |       |   |-- enums
    |   |       |   |   |-- IncomeType.java
    |   |       |   |   `-- package-info.java
    |   |       |   |-- package-info.java
    |   |       |   |-- RepositoryIncome.java
    |   |       |   `-- ServiceIncome.java
    |   |       |-- legal
    |   |       |   |-- controller
    |   |       |   |   |-- LegalController.java
    |   |       |   |   `-- package-info.java
    |   |       |   |-- dto
    |   |       |   |   |-- LegalDocumentResponse.java
    |   |       |   |   `-- package-info.java
    |   |       |   |-- enums
    |   |       |   |   |-- LegalDocumentType.java
    |   |       |   |   `-- package-info.java
    |   |       |   |-- exception
    |   |       |   |   |-- LegalDocumentNotFoundException.java
    |   |       |   |   `-- package-info.java
    |   |       |   `-- service
    |   |       |       |-- LegalService.java
    |   |       |       |-- LegalServiceImpl.java
    |   |       |       `-- package-info.java
    |   |       |-- merchant
    |   |       |   |-- MerchantDetectionService.java
    |   |       |   |-- MerchantPattern.java
    |   |       |   |-- MerchantPatternRepository.java
    |   |       |   `-- package-info.java
    |   |       |-- notifications
    |   |       |   |-- ControllerNotification.java
    |   |       |   |-- DTO
    |   |       |   |   |-- NotificationResponse.java
    |   |       |   |   `-- package-info.java
    |   |       |   |-- entity
    |   |       |   |   |-- Notification.java
    |   |       |   |   `-- package-info.java
    |   |       |   |-- enums
    |   |       |   |   |-- NotificationType.java
    |   |       |   |   `-- package-info.java
    |   |       |   |-- package-info.java
    |   |       |   |-- repository
    |   |       |   |   |-- NotificationRepository.java
    |   |       |   |   `-- package-info.java
    |   |       |   `-- service
    |   |       |       |-- NotificationService.java
    |   |       |       `-- package-info.java
    |   |       |-- package-info.java
    |   |       |-- rate_limit
    |   |       |   |-- config
    |   |       |   |   |-- package-info.java
    |   |       |   |   |-- RateLimitConfiguration.java
    |   |       |   |   `-- RateLimitProperties.java
    |   |       |   |-- dto
    |   |       |   |   |-- package-info.java
    |   |       |   |   `-- RateLimitErrorResponse.java
    |   |       |   |-- filter
    |   |       |   |   |-- package-info.java
    |   |       |   |   `-- RateLimitFilter.java
    |   |       |   |-- policy
    |   |       |   |   |-- EndpointPolicyResolver.java
    |   |       |   |   |-- package-info.java
    |   |       |   |   |-- RateLimitPolicy.java
    |   |       |   |   `-- RateLimitPolicyRegistry.java
    |   |       |   |-- scheduler
    |   |       |   |   |-- package-info.java
    |   |       |   |   `-- RateLimitCleanupTask.java
    |   |       |   |-- service
    |   |       |   |   |-- BucketStorageService.java
    |   |       |   |   |-- InMemoryBucketStorageService.java
    |   |       |   |   |-- package-info.java
    |   |       |   |   `-- RateLimitService.java
    |   |       |   |-- strategy
    |   |       |   |   |-- IpKeyResolver.java
    |   |       |   |   |-- KeyResolver.java
    |   |       |   |   |-- package-info.java
    |   |       |   |   `-- UserKeyResolver.java
    |   |       |   `-- util
    |   |       |       |-- ClientIpUtil.java
    |   |       |       |-- package-info.java
    |   |       |       `-- RateLimitResponseUtil.java
    |   |       |-- recurring
    |   |       |   |-- ControllerRecurring.java
    |   |       |   |-- DTO
    |   |       |   |   |-- package-info.java
    |   |       |   |   |-- RecurringRequest.java
    |   |       |   |   `-- RecurringResponse.java
    |   |       |   |-- entity
    |   |       |   |   |-- package-info.java
    |   |       |   |   `-- RecurringTransaction.java
    |   |       |   |-- enums
    |   |       |   |   |-- package-info.java
    |   |       |   |   |-- RecurringFrequency.java
    |   |       |   |   `-- RecurringType.java
    |   |       |   |-- package-info.java
    |   |       |   |-- RepositoryRecurringTransaction.java
    |   |       |   `-- services
    |   |       |       |-- package-info.java
    |   |       |       |-- RecurringExecutionService.java
    |   |       |       `-- RecurringScheduler.java
    |   |       |-- ServletInitializer.java
    |   |       |-- subscription
    |   |       |   |-- controllers
    |   |       |   |   |-- package-info.java
    |   |       |   |   `-- SubscriptionController.java
    |   |       |   |-- dto
    |   |       |   |   |-- CurrentSubscriptionResponse.java
    |   |       |   |   |-- package-info.java
    |   |       |   |   |-- PlanResponse.java
    |   |       |   |   `-- SubscriptionResponse.java
    |   |       |   |-- entities
    |   |       |   |   |-- package-info.java
    |   |       |   |   |-- Payment.java
    |   |       |   |   |-- Plan.java
    |   |       |   |   `-- Subscription.java
    |   |       |   |-- enums
    |   |       |   |   |-- BillingPeriod.java
    |   |       |   |   |-- package-info.java
    |   |       |   |   |-- PaymentStatus.java
    |   |       |   |   `-- SubscriptionStatus.java
    |   |       |   |-- package-info.java
    |   |       |   |-- PlanCodes.java
    |   |       |   |-- repository
    |   |       |   |   |-- package-info.java
    |   |       |   |   |-- RepositoryPayment.java
    |   |       |   |   |-- RepositoryPlan.java
    |   |       |   |   `-- RepositorySubscription.java
    |   |       |   |-- scheduler
    |   |       |   |   |-- package-info.java
    |   |       |   |   `-- SubscriptionRenewalScheduler.java
    |   |       |   `-- services
    |   |       |       |-- package-info.java
    |   |       |       |-- PaymentService.java
    |   |       |       |-- PlanService.java
    |   |       |       |-- SubscriptionService.java
    |   |       |       `-- SubscriptionValidationService.java
    |   |       |-- tranfers
    |   |       |   |-- ControllerTransfer.java
    |   |       |   |-- DTO
    |   |       |   |   |-- package-info.java
    |   |       |   |   |-- TransferenceRequest.java
    |   |       |   |   `-- TransferenceResponse.java
    |   |       |   |-- entities
    |   |       |   |   |-- package-info.java
    |   |       |   |   `-- Transfer.java
    |   |       |   |-- enums
    |   |       |   |   |-- package-info.java
    |   |       |   |   `-- TransferStatus.java
    |   |       |   |-- package-info.java
    |   |       |   |-- RepositoryTransfers.java
    |   |       |   `-- ServiceTransfers.java
    |   |       `-- wallet
    |   |           |-- ControllerWallet.java
    |   |           |-- DTO
    |   |           |   |-- package-info.java
    |   |           |   |-- UpdateWalletRequest.java
    |   |           |   |-- UserWalletsResponse.java
    |   |           |   |-- WalletRequest.java
    |   |           |   `-- WalletResponse.java
    |   |           |-- entities
    |   |           |   |-- package-info.java
    |   |           |   |-- Wallet.java
    |   |           |   `-- WalletUser.java
    |   |           |-- enums
    |   |           |   |-- Currency.java
    |   |           |   |-- package-info.java
    |   |           |   |-- WalletRole.java
    |   |           |   `-- WalletType.java
    |   |           |-- package-info.java
    |   |           |-- RepositoryWallet.java
    |   |           |-- RepositoryWalletUser.java
    |   |           `-- ServiceWallet.java
    |   `-- resources
    |       |-- application.properties
    |       |-- legal
    |       |   |-- cookies_en.md
    |       |   |-- cookies_es.md
    |       |   |-- privacy_en.md
    |       |   |-- privacy_es.md
    |       |   |-- terms_en.md
    |       |   `-- terms_es.md
    |       `-- templates
    |           |-- email-verification.html
    |           `-- forgot-password.html
    `-- test
        |-- java
        |   `-- Diaz
        |       `-- Dev
        |           `-- BFlow
        |               |-- BFlowApplicationTests.java
        |               |-- common
        |               |   |-- exception
        |               |   |   `-- InvalidCredentialsExceptionTest.java
        |               |   `-- response
        |               |       `-- ApiResponseTest.java
        |               |-- expenses
        |               |   `-- ServiceExpenseTest.java
        |               |-- income
        |               |   `-- ServiceIncomeTest.java
        |               |-- tranfers
        |               |   `-- ServiceTransfersTest.java
        |               `-- wallet
        |                   `-- ServiceWalletTest.java
        `-- resources
            `-- application-test.properties
```

**Total: 287 files**

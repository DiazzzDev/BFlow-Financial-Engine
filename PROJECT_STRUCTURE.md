# BFlow Studio

```
.
|-- bin
|   |-- docker-compose.yml
|   |-- Dockerfile
|   |-- LICENSE
|   |-- mvnw
|   |-- mvnw.cmd
|   |-- pom.xml
|   |-- README.md
|   |-- scripts
|   |-- src
|   |   |-- main
|   |   |   |-- java
|   |   |   |   `-- bflow
|   |   |   |       |-- auth
|   |   |   |       |   |-- controllers
|   |   |   |       |   |   |-- AuthController.class
|   |   |   |       |   |   |-- KeyRotationController.class
|   |   |   |       |   |   `-- UserController.class
|   |   |   |       |   |-- DTO
|   |   |   |       |   |   |-- AuthLoginRequest.class
|   |   |   |       |   |   |-- AuthMeResponse.class
|   |   |   |       |   |   |-- AuthRegisterRequest.class
|   |   |   |       |   |   |-- Record
|   |   |   |       |   |   |   |-- AuthLoginResponse.class
|   |   |   |       |   |   |   |-- AuthRequest.class
|   |   |   |       |   |   |   |-- AuthResponse.class
|   |   |   |       |   |   |   |-- RefreshRotationResult.class
|   |   |   |       |   |   |   `-- RefreshSession.class
|   |   |   |       |   |   `-- user
|   |   |   |       |   |       |-- UpdateUserProfileRequest.class
|   |   |   |       |   |       |-- UserProfileResponse$UserProfileResponseBuilder.class
|   |   |   |       |   |       `-- UserProfileResponse.class
|   |   |   |       |   |-- entities
|   |   |   |       |   |   |-- AuthAccount$AuthAccountBuilder.class
|   |   |   |       |   |   |-- AuthAccount.class
|   |   |   |       |   |   |-- RefreshToken.class
|   |   |   |       |   |   |-- Role.class
|   |   |   |       |   |   |-- User$UserBuilder.class
|   |   |   |       |   |   `-- User.class
|   |   |   |       |   |-- enums
|   |   |   |       |   |   |-- AuthProvider.class
|   |   |   |       |   |   `-- UserStatus.class
|   |   |   |       |   |-- repository
|   |   |   |       |   |   |-- RepositoryAuthAccount.class
|   |   |   |       |   |   |-- RepositoryRefreshToken.class
|   |   |   |       |   |   |-- RepositoryRole.class
|   |   |   |       |   |   `-- RepositoryUser.class
|   |   |   |       |   |-- security
|   |   |   |       |   |   |-- CorsConfig.class
|   |   |   |       |   |   |-- jwk
|   |   |   |       |   |   |   |-- Jwk$JwkBuilder.class
|   |   |   |       |   |   |   |-- Jwk.class
|   |   |   |       |   |   |   |-- JwkController.class
|   |   |   |       |   |   |   |-- JwkMapper.class
|   |   |   |       |   |   |   |-- JwkService.class
|   |   |   |       |   |   |   |-- JwkServiceImpl.class
|   |   |   |       |   |   |-- jwt
|   |   |   |       |   |   |   |-- JwtAuthenticationFilter.class
|   |   |   |       |   |   |   |-- JwtConfig.class
|   |   |   |       |   |   |   |-- JwtKeyConfig.class
|   |   |   |       |   |   |   |-- JwtService.class
|   |   |   |       |   |   |   |-- JwtServiceImpl.class
|   |   |   |       |   |   |   |-- RSAKeyLoader.class
|   |   |   |       |   |   |   |-- RsaKeyPair.class
|   |   |   |       |   |   |   `-- RsaKeyProvider.class
|   |   |   |       |   |   |-- OAuth2SuccessHandler.class
|   |   |   |       |   |   |-- PasswordEncoderConfig.class
|   |   |   |       |   |   `-- SecurityConfig.class
|   |   |   |       |   `-- services
|   |   |   |       |       |-- AuthService.class
|   |   |   |       |       |-- ServiceRefreshToken.class
|   |   |   |       |       |-- UserService.class
|   |   |   |       |       `-- UserServiceImpl.class
|   |   |   |       |-- BFlowApplication.class
|   |   |   |       |-- common
|   |   |   |       |   |-- exception
|   |   |   |       |   |   |-- ApiError.class
|   |   |   |       |   |   |-- GlobalExceptionHandler.class
|   |   |   |       |   |   |-- InvalidCredentialsException.class
|   |   |   |       |   |   |-- NotFoundException.class
|   |   |   |       |   |   |-- ResourceNotFoundException.class
|   |   |   |       |   |   `-- WalletAccessDeniedException.class
|   |   |   |       |   |-- financial
|   |   |   |       |   |   |-- FinancialEntry.class
|   |   |   |       |   `-- response
|   |   |   |       |       |-- ApiResponse.class
|   |   |   |       |-- expenses
|   |   |   |       |   |-- ControllerExpense.class
|   |   |   |       |   |-- DTO
|   |   |   |       |   |   |-- ExpenseRequest.class
|   |   |   |       |   |   |-- ExpenseResponse.class
|   |   |   |       |   |-- entity
|   |   |   |       |   |   |-- Expense.class
|   |   |   |       |   |-- enums
|   |   |   |       |   |   |-- ExpenseType.class
|   |   |   |       |   |-- RepositoryExpense.class
|   |   |   |       |   `-- ServiceExpense.class
|   |   |   |       |-- income
|   |   |   |       |   |-- ControllerIncome.class
|   |   |   |       |   |-- DTO
|   |   |   |       |   |   |-- IncomeRequest.class
|   |   |   |       |   |   |-- IncomeResponse.class
|   |   |   |       |   |-- entity
|   |   |   |       |   |   |-- Income.class
|   |   |   |       |   |-- enums
|   |   |   |       |   |   |-- IncomeType.class
|   |   |   |       |   |-- RepositoryIncome.class
|   |   |   |       |   `-- ServiceIncome.class
|   |   |   |       |-- ServletInitializer.class
|   |   |   |       |-- tranfers
|   |   |   |       |   |-- ControllerTransfer.class
|   |   |   |       |   |-- DTO
|   |   |   |       |   |   |-- TransferenceRequest.class
|   |   |   |       |   |   `-- TransferenceResponse.class
|   |   |   |       |   |-- entities
|   |   |   |       |   |   `-- Transfer.class
|   |   |   |       |   |-- enums
|   |   |   |       |   |   `-- TransferStatus.class
|   |   |   |       |   |-- RepositoryTransfers.class
|   |   |   |       |   `-- ServiceTransfers.class
|   |   |   |       `-- wallet
|   |   |   |           |-- ControllerWallet.class
|   |   |   |           |-- DTO
|   |   |   |           |   |-- UserWalletsResponse.class
|   |   |   |           |   |-- WalletRequest$WalletRequestBuilder.class
|   |   |   |           |   |-- WalletRequest.class
|   |   |   |           |   |-- WalletResponse$WalletResponseBuilder.class
|   |   |   |           |   `-- WalletResponse.class
|   |   |   |           |-- entities
|   |   |   |           |   |-- Wallet.class
|   |   |   |           |   `-- WalletUser.class
|   |   |   |           |-- enums
|   |   |   |           |   |-- Currency.class
|   |   |   |           |   |-- WalletRole.class
|   |   |   |           |   `-- WalletType.class
|   |   |   |           |-- RepositoryWallet.class
|   |   |   |           |-- RepositoryWalletUser.class
|   |   |   |           `-- ServiceWallet.class
|   |   |   `-- resources
|   |   |       |-- application.properties
|   |   |       `-- keys
|   |   |           |-- private.pem
|   |   |           `-- public.pem
|   |   `-- test
|   |       |-- java
|   |       |   `-- Diaz
|   |       |       `-- Dev
|   |       |           `-- BFlow
|   |       |               |-- auth
|   |       |               |   |-- controllers
|   |       |               |   |   |-- AuthControllerCookieSecurityTest.class
|   |       |               |   |   `-- UserControllerTest.class
|   |       |               |   |-- DTO
|   |       |               |   |   `-- AuthDTOTest.class
|   |       |               |   |-- entities
|   |       |               |   |   |-- RefreshTokenEntityTest.class
|   |       |               |   |   `-- UserEntityTest.class
|   |       |               |   |-- security
|   |       |               |   |   |-- jwk
|   |       |               |   |   |-- jwt
|   |       |               |   |   `-- OAuth2SuccessHandlerTest.class
|   |       |               |   `-- services
|   |       |               |       |-- AuthServiceTest.class
|   |       |               |       |-- ServiceRefreshTokenTest.class
|   |       |               |       `-- UserServiceTest.class
|   |       |               |-- BFlowApplicationTests.class
|   |       |               |-- common
|   |       |               |   |-- exception
|   |       |               |   |   `-- InvalidCredentialsExceptionTest.class
|   |       |               |   `-- response
|   |       |               |       `-- ApiResponseTest.class
|   |       |               |-- expenses
|   |       |               |   `-- ServiceExpenseTest.class
|   |       |               |-- income
|   |       |               |   `-- ServiceIncomeTest.class
|   |       |               |-- tranfers
|   |       |               |   `-- ServiceTransfersTest.class
|   |       |               `-- wallet
|   |       |                   |-- entities
|   |       |                   `-- ServiceWalletTest.class
|   |       `-- resources
|   |           |-- application-test.properties
|   |           `-- test-keys
|   |               |-- private-test.pem
|   |               `-- public-test.pem
|   `-- target
|   |   |-- BFlow-0.0.1-SNAPSHOT.jar
|   |   |-- BFlow-0.0.1-SNAPSHOT.jar.original
|   |   |-- checkstyle-cachefile
|   |   |-- checkstyle-checker.xml
|   |   |-- checkstyle-result.xml
|   |   |-- classes
|   |   |   |-- application.properties
|   |   |   |-- bflow
|   |   |   |   |-- auth
|   |   |   |   |   |-- controllers
|   |   |   |   |   |-- DTO
|   |   |   |   |   |   |-- Record
|   |   |   |   |   |   `-- user
|   |   |   |   |   |-- entities
|   |   |   |   |   |-- enums
|   |   |   |   |   |-- repository
|   |   |   |   |   |-- security
|   |   |   |   |   |   |-- jwk
|   |   |   |   |   |   `-- jwt
|   |   |   |   |   `-- services
|   |   |   |   |-- common
|   |   |   |   |   |-- exception
|   |   |   |   |   |-- financial
|   |   |   |   |   `-- response
|   |   |   |   |-- expenses
|   |   |   |   |   |-- DTO
|   |   |   |   |   |-- entity
|   |   |   |   |   `-- enums
|   |   |   |   |-- income
|   |   |   |   |   |-- DTO
|   |   |   |   |   |-- entity
|   |   |   |   |   `-- enums
|   |   |   |   |-- tranfers
|   |   |   |   |   |-- DTO
|   |   |   |   |   |-- entities
|   |   |   |   |   `-- enums
|   |   |   |   `-- wallet
|   |   |   |       |-- DTO
|   |   |   |       |-- entities
|   |   |   |       `-- enums
|   |   |   `-- keys
|   |   |       |-- private.pem
|   |   |       `-- public.pem
|   |   |-- generated-sources
|   |   |   `-- annotations
|   |   |-- jacoco.exec
|   |   |-- maven-archiver
|   |   |   `-- pom.properties
|   |   |-- maven-status
|   |   |   `-- maven-compiler-plugin
|   |   |       |-- compile
|   |   |       |   `-- default-compile
|   |   |       |       |-- createdFiles.lst
|   |   |       |       `-- inputFiles.lst
|   |   |       `-- testCompile
|   |   |           `-- default-testCompile
|   |   |               |-- createdFiles.lst
|   |   |               `-- inputFiles.lst
|   |   |-- site
|   |   |   `-- jacoco
|   |   |-- surefire-reports
|   |   `-- test-classes
|   `-- target_test-classes
|       `-- Diaz
|-- CODE_OF_CONDUCT.md
|-- CONTRIBUTING.md
|-- docker-compose.yml
|-- Dockerfile
|-- LICENSE
|-- mvnw
|-- mvnw.cmd
|-- pom.xml
|-- README.md
|-- scripts
|-- src
|   |-- main
|   |   |-- java
|   |   |   `-- bflow
|   |   |       |-- auth
|   |   |       |   |-- controllers
|   |   |       |   |   |-- AuthController.java
|   |   |       |   |   |-- KeyRotationController.java
|   |   |       |   |   `-- UserController.java
|   |   |       |   |-- DTO
|   |   |       |   |   |-- AuthLoginRequest.java
|   |   |       |   |   |-- AuthMeResponse.java
|   |   |       |   |   |-- AuthRegisterRequest.java
|   |   |       |   |   |-- Record
|   |   |       |   |   |   |-- AuthLoginResponse.java
|   |   |       |   |   |   |-- AuthRequest.java
|   |   |       |   |   |   |-- AuthResponse.java
|   |   |       |   |   |   |-- ForgotPasswordRequest.java
|   |   |       |   |   |   |-- PasswordConstraints.java
|   |   |       |   |   |   |-- RefreshRotationResult.java
|   |   |       |   |   |   |-- RefreshSession.java
|   |   |       |   |   |   `-- ResetPasswordRequest.java
|   |   |       |   |   `-- user
|   |   |       |   |       |-- UpdateUserProfileRequest.java
|   |   |       |   |       `-- UserProfileResponse.java
|   |   |       |   |-- entities
|   |   |       |   |   |-- AuthAccount.java
|   |   |       |   |   |-- EmailVerificationToken.java
|   |   |       |   |   |-- PasswordResetToken.java
|   |   |       |   |   |-- RefreshToken.java
|   |   |       |   |   |-- Role.java
|   |   |       |   |   `-- User.java
|   |   |       |   |-- enums
|   |   |       |   |   |-- AuthProvider.java
|   |   |       |   |   `-- UserStatus.java
|   |   |       |   |-- repository
|   |   |       |   |   |-- EmailVerificationTokenRepository.java
|   |   |       |   |   |-- PasswordResetTokenRepository.java
|   |   |       |   |   |-- RepositoryAuthAccount.java
|   |   |       |   |   |-- RepositoryRefreshToken.java
|   |   |       |   |   |-- RepositoryRole.java
|   |   |       |   |   `-- RepositoryUser.java
|   |   |       |   |-- security
|   |   |       |   |   |-- CorsConfig.java
|   |   |       |   |   |-- jwk
|   |   |       |   |   |   |-- JwkController.java
|   |   |       |   |   |   |-- Jwk.java
|   |   |       |   |   |   |-- JwkMapper.java
|   |   |       |   |   |   |-- JwkServiceImpl.java
|   |   |       |   |   |   |-- JwkService.java
|   |   |       |   |   |-- jwt
|   |   |       |   |   |   |-- JwtAuthenticationFilter.java
|   |   |       |   |   |   |-- JwtConfig.java
|   |   |       |   |   |   |-- JwtKeyConfig.java
|   |   |       |   |   |   |-- JwtServiceImpl.java
|   |   |       |   |   |   |-- JwtService.java
|   |   |       |   |   |   |-- RSAKeyLoader.java
|   |   |       |   |   |   |-- RsaKeyPair.java
|   |   |       |   |   |   `-- RsaKeyProvider.java
|   |   |       |   |   |-- OAuth2FailureHandler.java
|   |   |       |   |   |-- OAuth2SuccessHandler.java
|   |   |       |   |   |-- PasswordEncoderConfig.java
|   |   |       |   |   `-- SecurityConfig.java
|   |   |       |   |-- services
|   |   |       |   |   |-- AuthService.java
|   |   |       |   |   |-- EmailVerificationServiceImpl.java
|   |   |       |   |   |-- EmailVerificationService.java
|   |   |       |   |   |-- PasswordResetService.java
|   |   |       |   |   |-- ServiceRefreshToken.java
|   |   |       |   |   |-- UserServiceImpl.java
|   |   |       |   |   `-- UserService.java
|   |   |       |   `-- utils
|   |   |       |       `-- SecureTokenProvider.java
|   |   |       |-- BFlowApplication.java
|   |   |       |-- budget
|   |   |       |   |-- ControllerBudget.java
|   |   |       |   |-- DTO
|   |   |       |   |   |-- BudgetPatchRequest.java
|   |   |       |   |   |-- BudgetRequest.java
|   |   |       |   |   |-- BudgetResponse.java
|   |   |       |   |   |-- BudgetSummaryResponse.java
|   |   |       |   |-- entity
|   |   |       |   |   |-- Budget.java
|   |   |       |   |-- enums
|   |   |       |   |   |-- BudgetScope.java
|   |   |       |   |   |-- BudgetStatus.java
|   |   |       |   |   `-- PeriodType.java
|   |   |       |   |-- RepositoryBudget.java
|   |   |       |   `-- services
|   |   |       |       |-- BudgetAlertService.java
|   |   |       |       |-- BudgetCalculationService.java
|   |   |       |       |-- BudgetLifecycleService.java
|   |   |       |       |-- BudgetOverlapValidationService.java
|   |   |       |       |-- BudgetService.java
|   |   |       |       |-- BudgetValidationService.java
|   |   |       |-- category
|   |   |       |   |-- CategorySeeder.java
|   |   |       |   |-- CategoryValidator.java
|   |   |       |   |-- ControllerCategory.java
|   |   |       |   |-- DTO
|   |   |       |   |   |-- CategoryRequest.java
|   |   |       |   |   |-- CategoryResponse.java
|   |   |       |   |-- entity
|   |   |       |   |   |-- Category.java
|   |   |       |   |-- enums
|   |   |       |   |   |-- CategoryType.java
|   |   |       |   |-- RepositoryCategory.java
|   |   |       |   `-- ServiceCategory.java
|   |   |       |-- common
|   |   |       |   |-- aws
|   |   |       |   |   |-- config
|   |   |       |   |   |   |-- AwsSesConfig.java
|   |   |       |   |   `-- service
|   |   |       |   |       |-- EmailTemplateService.java
|   |   |       |   |       `-- SesEmailService.java
|   |   |       |   |-- exception
|   |   |       |   |   |-- ApiError.java
|   |   |       |   |   |-- BudgetNotFoundException.java
|   |   |       |   |   |-- BudgetOverlapException.java
|   |   |       |   |   |-- GlobalExceptionHandler.java
|   |   |       |   |   |-- InvalidBudgetDateException.java
|   |   |       |   |   |-- InvalidBudgetScopeException.java
|   |   |       |   |   |-- InvalidBudgetThresholdException.java
|   |   |       |   |   |-- InvalidCredentialsException.java
|   |   |       |   |   |-- NotFoundException.java
|   |   |       |   |   |-- ResourceNotFoundException.java
|   |   |       |   |   `-- WalletAccessDeniedException.java
|   |   |       |   |-- financial
|   |   |       |   |   |-- BaseTransactionRequest.java
|   |   |       |   |   |-- Transaction.java
|   |   |       |   |   `-- TransactionMapper.java
|   |   |       |   `-- response
|   |   |       |       |-- ApiResponse.java
|   |   |       |-- expenses
|   |   |       |   |-- controllers
|   |   |       |   |   |-- ControllerExpense.java
|   |   |       |   |   `-- QuickExpenseController.java
|   |   |       |   |-- DTO
|   |   |       |   |   |-- ExpenseRequest.java
|   |   |       |   |   |-- ExpenseResponse.java
|   |   |       |   |   `-- QuickExpenseRequest.java
|   |   |       |   |-- entity
|   |   |       |   |   |-- Expense.java
|   |   |       |   |-- enums
|   |   |       |   |   |-- ExpenseType.java
|   |   |       |   |-- RepositoryExpense.java
|   |   |       |   `-- services
|   |   |       |       |-- QuickExpenseService.java
|   |   |       |       `-- ServiceExpense.java
|   |   |       |-- income
|   |   |       |   |-- ControllerIncome.java
|   |   |       |   |-- DTO
|   |   |       |   |   |-- IncomeRequest.java
|   |   |       |   |   |-- IncomeResponse.java
|   |   |       |   |-- entity
|   |   |       |   |   |-- Income.java
|   |   |       |   |-- enums
|   |   |       |   |   |-- IncomeType.java
|   |   |       |   |-- RepositoryIncome.java
|   |   |       |   `-- ServiceIncome.java
|   |   |       |-- legal
|   |   |       |   |-- controller
|   |   |       |   |   |-- LegalController.java
|   |   |       |   |-- dto
|   |   |       |   |   |-- LegalDocumentResponse.java
|   |   |       |   |-- enums
|   |   |       |   |   |-- LegalDocumentType.java
|   |   |       |   |-- exception
|   |   |       |   |   |-- LegalDocumentNotFoundException.java
|   |   |       |   `-- service
|   |   |       |       |-- LegalServiceImpl.java
|   |   |       |       |-- LegalService.java
|   |   |       |-- merchant
|   |   |       |   |-- MerchantDetectionService.java
|   |   |       |   |-- MerchantPattern.java
|   |   |       |   |-- MerchantPatternRepository.java
|   |   |       |-- notifications
|   |   |       |   |-- ControllerNotification.java
|   |   |       |   |-- DTO
|   |   |       |   |   |-- NotificationResponse.java
|   |   |       |   |-- entity
|   |   |       |   |   |-- Notification.java
|   |   |       |   |-- enums
|   |   |       |   |   |-- NotificationType.java
|   |   |       |   |-- repository
|   |   |       |   |   |-- NotificationRepository.java
|   |   |       |   `-- service
|   |   |       |       |-- NotificationService.java
|   |   |       |-- rate_limit
|   |   |       |   |-- config
|   |   |       |   |   |-- RateLimitConfiguration.java
|   |   |       |   |   `-- RateLimitProperties.java
|   |   |       |   |-- dto
|   |   |       |   |   `-- RateLimitErrorResponse.java
|   |   |       |   |-- filter
|   |   |       |   |   `-- RateLimitFilter.java
|   |   |       |   |-- policy
|   |   |       |   |   |-- EndpointPolicyResolver.java
|   |   |       |   |   |-- RateLimitPolicy.java
|   |   |       |   |   `-- RateLimitPolicyRegistry.java
|   |   |       |   |-- scheduler
|   |   |       |   |   `-- RateLimitCleanupTask.java
|   |   |       |   |-- service
|   |   |       |   |   |-- BucketStorageService.java
|   |   |       |   |   |-- InMemoryBucketStorageService.java
|   |   |       |   |   `-- RateLimitService.java
|   |   |       |   |-- strategy
|   |   |       |   |   |-- IpKeyResolver.java
|   |   |       |   |   |-- KeyResolver.java
|   |   |       |   |   `-- UserKeyResolver.java
|   |   |       |   `-- util
|   |   |       |       |-- ClientIpUtil.java
|   |   |       |       `-- RateLimitResponseUtil.java
|   |   |       |-- recurring
|   |   |       |   |-- ControllerRecurring.java
|   |   |       |   |-- DTO
|   |   |       |   |   |-- RecurringRequest.java
|   |   |       |   |   `-- RecurringResponse.java
|   |   |       |   |-- entity
|   |   |       |   |   `-- RecurringTransaction.java
|   |   |       |   |-- enums
|   |   |       |   |   |-- RecurringFrequency.java
|   |   |       |   |   `-- RecurringType.java
|   |   |       |   |-- RepositoryRecurringTransaction.java
|   |   |       |   `-- services
|   |   |       |       |-- RecurringExecutionService.java
|   |   |       |       `-- RecurringScheduler.java
|   |   |       |-- ServletInitializer.java
|   |   |       |-- tranfers
|   |   |       |   |-- ControllerTransfer.java
|   |   |       |   |-- DTO
|   |   |       |   |   |-- TransferenceRequest.java
|   |   |       |   |   `-- TransferenceResponse.java
|   |   |       |   |-- entities
|   |   |       |   |   `-- Transfer.java
|   |   |       |   |-- enums
|   |   |       |   |   `-- TransferStatus.java
|   |   |       |   |-- RepositoryTransfers.java
|   |   |       |   `-- ServiceTransfers.java
|   |   |       `-- wallet
|   |   |           |-- ControllerWallet.java
|   |   |           |-- DTO
|   |   |           |   |-- UpdateWalletRequest.java
|   |   |           |   |-- UserWalletsResponse.java
|   |   |           |   |-- WalletRequest.java
|   |   |           |   `-- WalletResponse.java
|   |   |           |-- entities
|   |   |           |   |-- Wallet.java
|   |   |           |   `-- WalletUser.java
|   |   |           |-- enums
|   |   |           |   |-- Currency.java
|   |   |           |   |-- WalletRole.java
|   |   |           |   `-- WalletType.java
|   |   |           |-- RepositoryWallet.java
|   |   |           |-- RepositoryWalletUser.java
|   |   |           `-- ServiceWallet.java
|   |   `-- resources
|   |       |-- application.properties
|   |       |-- keys
|   |       |   |-- private.pem
|   |       |   `-- public.pem
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
|       |               |-- auth
|       |               |   |-- controllers
|       |               |   |   |-- AuthControllerCookieSecurityTest.java
|       |               |   |   `-- UserControllerTest.java
|       |               |   |-- DTO
|       |               |   |   `-- AuthDTOTest.java
|       |               |   |-- entities
|       |               |   |   |-- RefreshTokenEntityTest.java
|       |               |   |   `-- UserEntityTest.java
|       |               |   |-- security
|       |               |   |   |-- jwk
|       |               |   |   |   `-- JwkServiceTest.java
|       |               |   |   |-- jwt
|       |               |   |   |   |-- JwtAuthenticationFilterTest.java
|       |               |   |   |   |-- JwtServiceImplTest.java
|       |               |   |   |   |-- RSAKeyLoaderTest.java
|       |               |   |   |   |-- RsaKeyPairTest.java
|       |               |   |   |   `-- RsaKeyProviderTest.java
|       |               |   |   `-- OAuth2SuccessHandlerTest.java
|       |               |   `-- services
|       |               |       |-- AuthServiceTest.java
|       |               |       |-- ServiceRefreshTokenTest.java
|       |               |       `-- UserServiceTest.java
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
|           |-- application-test.properties
|           `-- test-keys
|               |-- private-test.pem
|               `-- public-test.pem
|-- target
|   |-- BFlow-0.0.1-SNAPSHOT.jar
|   |-- BFlow-0.0.1-SNAPSHOT.jar.original
|   |-- classes
|   |   |-- application.properties
|   |   |-- bflow
|   |   |-- git.properties
|   |   |-- keys
|   |   |-- legal
|   |   |-- META-INF
|   |   `-- templates
|   |-- generated-sources
|   |   `-- annotations
|   |-- generated-test-sources
|   |   `-- test-annotations
|   |-- jacoco.exec
|   |-- maven-archiver
|   |-- maven-status
|   |-- site
|   |-- surefire-reports
|   `-- test-classes
`-- target_test-classes
    `-- Diaz
```

**Total: 1524 files**

# ADR-0001: Migrate Authentication to AWS Cognito

- **Status:** Accepted
- **Date:** 2026-07-04

## Context

The initial authentication implementation evolved organically during the early stages of the project.

It combined multiple authentication mechanisms, including:

- Spring Security
- Manual OAuth2 configuration
- HTTP-only cookies
- Custom RS256 JWT generation and validation
- Social login integrations
- Manual authentication filters
- Custom authorization logic

Although functional, this approach introduced several architectural challenges:

- Authentication logic was distributed across dozens of configuration and security classes.
- JWT signing, validation, and authorization were implemented and maintained internally.
- There was no refresh token strategy.
- Security responsibilities that are typically delegated to an Identity Provider remained inside the application.
- The authentication flow became difficult to understand for new contributors due to the amount of custom infrastructure involved.
- Future features such as MFA, federation, account recovery, and enterprise identity integration would require additional custom development.

After evaluating the long-term maintainability of the authentication subsystem, a managed Identity Provider was considered.

The following alternatives were evaluated:

- Clerk
- Auth0
- AWS Cognito

## Decision

The project adopts **AWS Cognito** as the official Identity Provider.

Authentication is delegated to Cognito while the application acts exclusively as an OAuth2 Resource Server responsible for validating JWT Bearer Tokens.

The frontend authenticates users using the OAuth2 Authorization Code Flow with PKCE through AWS Amplify.

The backend no longer generates or signs JWTs.

Instead, it validates Cognito-issued access tokens using Spring Security's OAuth2 Resource Server support.

Application users continue to exist in PostgreSQL and are linked to Cognito identities through the `cognitoSub` field.

User resolution is centralized through `CurrentUserService`, which maps the authenticated JWT subject (`sub`) to the corresponding application user.

## Consequences

### Positive

- Authentication responsibilities are delegated to a managed Identity Provider.
- JWT generation, signing, key rotation, and validation follow AWS-managed security practices.
- Native support for refresh tokens, MFA, federation, password recovery, and social identity providers.
- Significantly reduced authentication infrastructure maintained by the project.
- Authentication becomes stateless through Bearer Tokens.
- Security configuration becomes considerably easier to understand.
- Future integrations with AWS services become simpler.
- Authentication now follows a widely adopted enterprise architecture.

### Negative

- The application now depends on AWS Cognito availability.
- Local development requires Cognito credentials or valid development tokens.
- User synchronization between Cognito and PostgreSQL must be maintained.

## Implementation Notes

The migration resulted in a significant simplification of the authentication layer.

Approximately 65 authentication-related files responsible for manual JWT handling, security configuration, refresh token management, and social login orchestration were removed or replaced by the native Spring Security OAuth2 Resource Server integration.

Business authorization remains inside the application while authentication is delegated entirely to AWS Cognito.
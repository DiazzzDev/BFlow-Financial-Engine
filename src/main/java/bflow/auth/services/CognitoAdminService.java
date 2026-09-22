package bflow.auth.services;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.services.cognitoidentityprovider.CognitoIdentityProviderClient;
import software.amazon.awssdk.services.cognitoidentityprovider.model.AdminDeleteUserRequest;
import software.amazon.awssdk.services.cognitoidentityprovider.model.UserNotFoundException;

/**
 * Administrative operations against the Cognito user pool, used by
 * account hard deletion. Deleting the Cognito user frees the email/
 * username so the person can register again as a brand-new user.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CognitoAdminService {

    private final CognitoIdentityProviderClient cognitoClient;

    @Value("${aws.cognito.user-pool-id}")
    private String userPoolId;

    /**
     * Permanently deletes the given user from the Cognito user pool.
     * Idempotent: a user already absent from Cognito is treated as
     * success, not an error, so retries of a partially-completed
     * hard delete don't fail on this step.
     *
     * @param cognitoSub the Cognito {@code sub} to delete
     */
    public void deleteUser(final String cognitoSub) {

        if (cognitoSub == null || cognitoSub.isBlank()) {
            log.warn("Skipping Cognito deletion — user has no "
                    + "cognitoSub linked (never completed sign-in?)");
            return;
        }

        try {
            cognitoClient.adminDeleteUser(AdminDeleteUserRequest.builder()
                    .userPoolId(userPoolId)
                    .username(cognitoSub)
                    .build());
        } catch (UserNotFoundException ex) {
            log.info("Cognito user {} already absent — treating as "
                    + "already deleted", cognitoSub);
        }
    }
}
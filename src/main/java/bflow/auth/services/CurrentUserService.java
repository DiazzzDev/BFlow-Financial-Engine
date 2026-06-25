package bflow.auth.services;

import bflow.auth.entities.User;
import bflow.auth.repository.RepositoryUser;
import bflow.common.exception.NotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public final class CurrentUserService {

    /**
     * Repository used to resolve users from Cognito identities.
     */
    private final RepositoryUser repositoryUser;

    /**
     * Retrieves the currently authenticated user using the Cognito subject
     * contained in the JWT token.
     *
     * @param authentication current authentication
     * @return authenticated user
     * @throws NotFoundException when the user cannot be found
     */
    public User getCurrentUser(final Authentication authentication) {

        Jwt jwt = (Jwt) authentication.getPrincipal();

        String cognitoSub = jwt.getSubject();

        return repositoryUser
                .findByCognitoSub(cognitoSub)
                .orElseThrow(() ->
                        new NotFoundException("User not found"));
    }
}

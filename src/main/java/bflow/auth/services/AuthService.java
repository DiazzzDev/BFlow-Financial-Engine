package bflow.auth.services;

import bflow.auth.DTO.UserMeResponse;
import bflow.auth.entities.User;
import bflow.auth.repository.RepositoryAuthAccount;
import bflow.auth.repository.RepositoryUser;
import bflow.common.exception.ResourceNotFoundException;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Service providing core authentication and registration business logic.
 */
@Service
@RequiredArgsConstructor
@Transactional
public class AuthService {

    /** Repository for authentication account data. */
    private final RepositoryAuthAccount authAccountRepository;
    /** Repository for core user profile data. */
    private final RepositoryUser userRepository;

    /**
     * Finds a user by their unique identifier.
     * @param userId the user UUID.
     * @return the found User.
     */
    public User findById(final UUID userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new IllegalStateException("User not found"));
    }

    /**
     * Retrieves the currently authenticated user based on the JWT subject.
     *
     * @param authentication current authentication
     * @return authenticated user information
     * @throws ResourceNotFoundException if the user cannot be resolved
    */
    @Transactional(readOnly = true)
    public UserMeResponse getCurrentUser(
            final Authentication authentication
    ) {

        if (!(authentication instanceof JwtAuthenticationToken jwtAuth)) {
            throw new ResourceNotFoundException(
                    "Invalid authentication type"
            );
        }

        String cognitoSub =
                jwtAuth.getToken()
                        .getClaimAsString("sub");

        User user = userRepository
                .findByCognitoSub(cognitoSub)
                .orElseThrow(() ->
                        new ResourceNotFoundException(
                                "User not found"
                        ));

        return new UserMeResponse(
                user.getId(),
                user.getEmail(),
                List.copyOf(user.getRoles()),
                null,
                List.of(),
                null
        );
    }
}

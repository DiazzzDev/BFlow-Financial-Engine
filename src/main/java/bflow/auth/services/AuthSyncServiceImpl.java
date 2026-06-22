package bflow.auth.services;

import bflow.auth.DTO.Record.SyncUserRequest;
import bflow.auth.DTO.Record.SyncUserResponse;
import bflow.auth.entities.User;
import bflow.auth.enums.UserStatus;
import bflow.auth.repository.RepositoryUser;
import lombok.RequiredArgsConstructor;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import java.util.Optional;
import java.util.Set;

@Service
@RequiredArgsConstructor
public final class AuthSyncServiceImpl implements AuthSyncService {

    /**
     * User repository.
     */
    private final RepositoryUser repositoryUser;

    /**
     * Bootstrap service for newly created users.
     */
    private final AuthBootstrapService authBootstrapService;

    /**
     * Synchronizes a Cognito user with the local database.
     *
     * @param jwt authenticated JWT
     * @param request synchronization request
     * @return synchronization result
     */
    @Override
    public SyncUserResponse synchronize(
        final Jwt jwt,
        final SyncUserRequest request
    ) {

        String sub = jwt.getSubject();

        String email = request.email();

        Optional<User> existingBySub =
                repositoryUser.findByCognitoSub(sub);

        if (existingBySub.isPresent()) {

            User user = existingBySub.get();

            return new SyncUserResponse(
                    user.getId(),
                    user.getEmail(),
                    String.join(",", user.getRoles()),
                    false
            );
        }

        Optional<User> existingByEmail = repositoryUser.findByEmail(email);

        if (existingByEmail.isPresent()) {

            User user = existingByEmail.get();

            user.setCognitoSub(sub);

            if (Boolean.TRUE.equals(request.emailVerified())) {
                user.setEmailVerified(true);
            }

            repositoryUser.save(user);

            //authBootstrapService.bootstrap(user);

            return new SyncUserResponse(
                    user.getId(),
                    user.getEmail(),
                    String.join(",", user.getRoles()),
                    false
            );
        }
        User newUser =
                User.builder()
                        .cognitoSub(sub)
                        .email(email)
                        .status(UserStatus.ACTIVE)
                        .emailVerified(
                                Boolean.TRUE.equals(
                                        request.emailVerified()
                                )
                        )
                        .roles(Set.of("ROLE_USER"))
                        .build();

        repositoryUser.save(newUser);

        authBootstrapService.bootstrap(newUser);

        return new SyncUserResponse(
                newUser.getId(),
                newUser.getEmail(),
                "ROLE_USER",
                true
        );
    }
}

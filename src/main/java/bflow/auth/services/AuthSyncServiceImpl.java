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
import org.springframework.transaction.annotation.Transactional;
import bflow.auth.security.CognitoIdTokenValidator;


@Service
@RequiredArgsConstructor
public class AuthSyncServiceImpl implements AuthSyncService {

    private final RepositoryUser repositoryUser;
    private final AuthBootstrapService authBootstrapService;
    private final CognitoIdTokenValidator idTokenValidator;

    @Override
    @Transactional
    public SyncUserResponse synchronize(
            final Jwt accessJwt,
            final SyncUserRequest request
    ) {
        // Validate idToken signature with Cognito JWKs — no manual parsing
        Jwt idToken = idTokenValidator.validate(request.idToken());

        String sub = idToken.getSubject();
        String email = idToken.getClaimAsString("email");
        Boolean emailVerified = idToken.getClaimAsBoolean("email_verified");

        // Verify sub consistency between access token and id token
        if (!accessJwt.getSubject().equals(sub)) {
            throw new IllegalArgumentException(
                    "Token subject mismatch"
            );
        }

        Optional<User> existingBySub = repositoryUser.findByCognitoSub(sub);
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
            if (Boolean.TRUE.equals(emailVerified)) {
                user.setEmailVerified(true);
            }
            repositoryUser.save(user);
            return new SyncUserResponse(
                    user.getId(),
                    user.getEmail(),
                    String.join(",", user.getRoles()),
                    false
            );
        }

        User newUser = User.builder()
                .cognitoSub(sub)
                .email(email)
                .status(UserStatus.ACTIVE)
                .emailVerified(Boolean.TRUE.equals(emailVerified))
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
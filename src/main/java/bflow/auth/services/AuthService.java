package bflow.auth.services;

import bflow.auth.DTO.AuthRegisterRequest;
import bflow.auth.entities.AuthAccount;
import bflow.auth.entities.User;
import bflow.auth.enums.AuthProvider;
import bflow.auth.enums.UserStatus;
import bflow.auth.repository.RepositoryAuthAccount;
import bflow.auth.repository.RepositoryUser;
import bflow.common.exception.InvalidCredentialsException;
import jakarta.validation.Valid;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

/**
 * Service providing core authentication and registration business logic.
 */
@Service
@RequiredArgsConstructor
public class AuthService {

    /** Repository for authentication account data. */
    private final RepositoryAuthAccount authAccountRepository;
    /** Repository for core user profile data. */
    private final RepositoryUser userRepository;
    /** Encoder for hashing and verifying passwords. */
    private final PasswordEncoder passwordEncoder;

    /**
     * Authenticates a user based on email and password.
     * @param email the user's email address.
     * @param password the plain-text password.
     * @return the authenticated User entity.
     * @throws InvalidCredentialsException if authentication fails.
     */
    public User authenticate(
            final String email,
            final String password
    ) {

        AuthAccount account = authAccountRepository
                .findActiveByLoginAndProvider(email, AuthProvider.LOCAL)
                .orElseThrow(InvalidCredentialsException::new);

        if (account.getPasswordHash() == null
                || !passwordEncoder.matches(
                        password, account.getPasswordHash())
        ) {
            throw new InvalidCredentialsException();
        }

        return account.getUser();
    }

    /**
     * Retrieves the roles associated with a specific user.
     * @param user the user entity.
     * @return a list of role names.
     */
    public List<String> getRoles(final User user) {
        return List.copyOf(user.getRoles());
    }

    /**
     * Registers a new user and creates a local auth account.
     * @param dto the registration data.
     */
    public void register(@Valid final AuthRegisterRequest dto) {

        boolean exists = authAccountRepository
                .existsByProviderAndProviderUserId(
                        AuthProvider.LOCAL,
                        dto.getEmail()
                );

        if (exists) {
            throw new IllegalStateException("User already exists");
        }

        User user = new User();
        user.setEmail(dto.getEmail());
        user.setProvider(AuthProvider.LOCAL);
        user.setRoles(Set.of("ROLE_USER"));
        user.setStatus(UserStatus.ACTIVE);

        userRepository.save(user);

        AuthAccount account = new AuthAccount();
        account.setUser(user);
        account.setProvider(AuthProvider.LOCAL);
        account.setProviderUserId(dto.getEmail());
        account.setPasswordHash(passwordEncoder.encode(dto.getPassword()));
        account.setEnabled(true);

        authAccountRepository.save(account);
    }

    /**
     * Finds a user by their unique identifier.
     * @param userId the user UUID.
     * @return the found User.
     */
    public User findById(final UUID userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new IllegalStateException("User not found"));
    }
}

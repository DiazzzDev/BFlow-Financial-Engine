package Diaz.Dev.BFlow.auth.services;

import bflow.auth.DTO.Record.SyncUserRequest;
import bflow.auth.DTO.Record.SyncUserResponse;
import bflow.auth.DTO.UserMeResponse;
import bflow.auth.entities.User;
import bflow.auth.enums.NameSource;
import bflow.auth.enums.PictureSource;
import bflow.auth.enums.UserStatus;
import bflow.auth.mapper.UserMapper;
import bflow.auth.repository.RepositoryUser;
import bflow.auth.security.CognitoIdTokenValidator;
import bflow.auth.services.AuthBootstrapService;
import bflow.auth.services.AuthSyncService;
import bflow.auth.services.UserService;
import bflow.common.i18n.MessageService;
import bflow.subscription.dto.CurrentSubscriptionResponse;
import bflow.subscription.services.PlanLimitService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.security.oauth2.jwt.Jwt;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AuthSyncServiceTest {

    private RepositoryUser repositoryUser;
    private AuthBootstrapService authBootstrapService;
    private CognitoIdTokenValidator idTokenValidator;
    private UserMapper userMapper;
    private PlanLimitService planLimitService;
    private MessageService messageService;
    private UserService userService;
    private AuthSyncService service;

    @BeforeEach
    void setUp() {
        repositoryUser = mock(RepositoryUser.class);
        authBootstrapService = mock(AuthBootstrapService.class);
        idTokenValidator = mock(CognitoIdTokenValidator.class);
        userMapper = mock(UserMapper.class);
        planLimitService = mock(PlanLimitService.class);
        messageService = mock(MessageService.class);
        userService = mock(UserService.class);
        service = new AuthSyncService(
                repositoryUser,
                authBootstrapService,
                idTokenValidator,
                userMapper,
                planLimitService,
                messageService,
                userService);
        when(idTokenValidator.validate("id-token"))
                .thenAnswer(invocation -> token(
                        "subject", "user@example.com", true,
                        "New Name", "https://example.com/avatar.png"));
        when(planLimitService.getCurrentSubscriptionInfo(any()))
                .thenReturn(new CurrentSubscriptionResponse(
                        "FREE", "Free", null, Map.of(), Map.of()));
    }

    @Test
    void createsNewUserAndBootstrapsSession() {
        UUID userId = UUID.randomUUID();
        when(repositoryUser.findByCognitoSub("subject"))
                .thenReturn(Optional.empty());
        when(repositoryUser.findByEmail("user@example.com"))
                .thenReturn(Optional.empty());
        when(repositoryUser.save(any(User.class))).thenAnswer(invocation -> {
            User saved = invocation.getArgument(0);
            saved.setId(userId);
            return saved;
        });
        when(userMapper.toMeResponse(any(User.class)))
                .thenAnswer(invocation -> meResponse(
                        invocation.getArgument(0)));

        SyncUserResponse response = service.synchronize(
                token("subject", null, null, null, null),
                new SyncUserRequest("id-token"));

        assertTrue(response.isNewUser());
        assertEquals(userId, response.id());
        assertEquals("user@example.com", response.email());
        assertFalse(response.accountPendingDeletion());
        assertNull(response.deletionDaysRemaining());
        verify(authBootstrapService).bootstrap(any(User.class));
        verify(repositoryUser).findByEmail("user@example.com");
        ArgumentCaptor<User> savedUser = ArgumentCaptor.forClass(User.class);
        verify(repositoryUser).save(savedUser.capture());
        assertEquals("subject", savedUser.getValue().getCognitoSub());
        assertEquals("New Name", savedUser.getValue().getName());
        assertEquals(
                "https://example.com/avatar.png",
                savedUser.getValue().getPictureUrl());
        assertEquals(UserStatus.ACTIVE, savedUser.getValue().getStatus());
        assertTrue(savedUser.getValue().isEmailVerified());
    }

    @Test
    void linksExistingAccountByEmailAndSynchronizesVerifiedProfile() {
        UUID userId = UUID.randomUUID();
        User existing = User.builder()
                .id(userId)
                .email("user@example.com")
                .nameSource(NameSource.GOOGLE)
                .pictureSource(PictureSource.NONE)
                .build();
        when(repositoryUser.findByCognitoSub("subject"))
                .thenReturn(Optional.empty());
        when(repositoryUser.findByEmail("user@example.com"))
                .thenReturn(Optional.of(existing));
        when(userMapper.toMeResponse(existing)).thenReturn(meResponse(existing));

        SyncUserResponse response = service.synchronize(
                token("subject", null, null, null, null),
                new SyncUserRequest("id-token"));

        assertFalse(response.isNewUser());
        assertEquals("subject", existing.getCognitoSub());
        assertTrue(existing.isEmailVerified());
        assertEquals("New Name", existing.getName());
        assertEquals("https://example.com/avatar.png",
                existing.getPictureUrl());
        assertEquals(PictureSource.GOOGLE, existing.getPictureSource());
        verify(repositoryUser).save(existing);
    }

    @Test
    void preservesUserManagedNameAndS3PictureForExistingCognitoUser() {
        User existing = User.builder()
                .id(UUID.randomUUID())
                .email("user@example.com")
                .name("Chosen Name")
                .nameSource(NameSource.USER)
                .pictureUrl("https://cdn.example.com/upload.png")
                .pictureSource(PictureSource.S3)
                .build();
        when(repositoryUser.findByCognitoSub("subject"))
                .thenReturn(Optional.of(existing));
        when(userMapper.toMeResponse(existing)).thenReturn(meResponse(existing));

        SyncUserResponse response = service.synchronize(
                token("subject", null, null, null, null),
                new SyncUserRequest("id-token"));

        assertFalse(response.isNewUser());
        assertEquals("Chosen Name", existing.getName());
        assertEquals("https://cdn.example.com/upload.png",
                existing.getPictureUrl());
        verify(repositoryUser, never()).findByEmail(any());
        verify(repositoryUser).save(existing);
    }

    @Test
    void doesNotOverwriteBlankClaimsOrUnverifiedEmail() {
        User existing = User.builder()
                .id(UUID.randomUUID())
                .email("user@example.com")
                .name("Existing")
                .pictureUrl("https://cdn.example.com/existing.png")
                .build();
        when(idTokenValidator.validate("id-token"))
                .thenReturn(token(
                        "subject", "user@example.com", false, " ", ""));
        when(repositoryUser.findByCognitoSub("subject"))
                .thenReturn(Optional.empty());
        when(repositoryUser.findByEmail("user@example.com"))
                .thenReturn(Optional.of(existing));
        when(userMapper.toMeResponse(existing)).thenReturn(meResponse(existing));

        service.synchronize(
                token("subject", null, null, null, null),
                new SyncUserRequest("id-token"));

        assertFalse(existing.isEmailVerified());
        assertEquals("Existing", existing.getName());
        assertEquals("https://cdn.example.com/existing.png",
                existing.getPictureUrl());
        assertEquals(PictureSource.NONE, existing.getPictureSource());
    }

    @Test
    void rejectsIdTokenWhoseSubjectDiffersFromAccessToken() {
        when(messageService.get("auth.tokenSubjectMismatch"))
                .thenReturn("Subject mismatch");

        IllegalArgumentException error = assertThrows(
                IllegalArgumentException.class,
                () -> service.synchronize(
                        token("different-subject", null, null, null, null),
                        new SyncUserRequest("id-token")));

        assertEquals("Subject mismatch", error.getMessage());
        verify(repositoryUser, never()).findByCognitoSub(any());
    }

    @Test
    void returnsNullPlanWhenPlanLookupFails() {
        User existing = User.builder()
                .id(UUID.randomUUID())
                .email("user@example.com")
                .build();
        when(repositoryUser.findByCognitoSub("subject"))
                .thenReturn(Optional.of(existing));
        when(userMapper.toMeResponse(existing)).thenReturn(meResponse(existing));
        when(planLimitService.getCurrentSubscriptionInfo(existing.getId()))
                .thenThrow(new IllegalStateException("plan unavailable"));

        SyncUserResponse response = service.synchronize(
                token("subject", null, null, null, null),
                new SyncUserRequest("id-token"));

        assertNull(response.plan());
    }

    @Test
    void includesRemainingDeletionDaysForPendingAccount() {
        User pending = User.builder()
                .id(UUID.randomUUID())
                .email("user@example.com")
                .status(UserStatus.PENDING_DELETION)
                .build();
        when(repositoryUser.findByCognitoSub("subject"))
                .thenReturn(Optional.of(pending));
        when(userMapper.toMeResponse(pending)).thenReturn(meResponse(pending));
        when(userService.daysRemainingBeforeHardDelete(pending)).thenReturn(4L);

        SyncUserResponse response = service.synchronize(
                token("subject", null, null, null, null),
                new SyncUserRequest("id-token"));

        assertTrue(response.accountPendingDeletion());
        assertEquals(4L, response.deletionDaysRemaining());
    }

    private UserMeResponse meResponse(final User user) {
        return new UserMeResponse(
                user.getId(),
                List.copyOf(user.getRoles()),
                null,
                List.of(),
                null);
    }

    private Jwt token(
            final String subject,
            final String email,
            final Boolean verified,
            final String name,
            final String picture
    ) {
        Instant now = Instant.now();
        Jwt.Builder builder = Jwt.withTokenValue("token")
                .header("alg", "none")
                .subject(subject)
                .issuedAt(now)
                .expiresAt(now.plusSeconds(300));
        if (email != null) {
            builder.claim("email", email);
        }
        if (verified != null) {
            builder.claim("email_verified", verified);
        }
        if (name != null) {
            builder.claim("name", name);
        }
        if (picture != null) {
            builder.claim("picture", picture);
        }
        return builder.build();
    }
}

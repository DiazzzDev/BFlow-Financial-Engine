package Diaz.Dev.BFlow.auth.services;

import bflow.auth.DTO.Record.RefreshRotationResult;
import bflow.auth.entities.RefreshToken;
import bflow.auth.repository.RepositoryRefreshToken;
import bflow.auth.services.ServiceRefreshToken;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ServiceRefreshTokenTest {

    @Mock
    private RepositoryRefreshToken repository;

    @InjectMocks
    private ServiceRefreshToken service;

    private UUID userId;
    private String rawToken;
    private String tokenHash;

    @BeforeEach
    void setup() {
        MockitoAnnotations.openMocks(this);
        userId = UUID.randomUUID();
        rawToken = UUID.randomUUID().toString();
        tokenHash = org.apache.commons.codec.digest.DigestUtils.sha256Hex(rawToken);
    }

    @Test
    void testCreateRefreshToken() {
        when(repository.save(any(RefreshToken.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        service.create(userId, rawToken);

        verify(repository, times(1)).save(any(RefreshToken.class));
    }

    @Test
    void testCreateCallsSaveOnRepository() {
        service.create(userId, rawToken);

        verify(repository).save(any(RefreshToken.class));
    }

    @Test
    void testRotateToken() {
        RefreshToken existing = new RefreshToken();
        existing.setId(UUID.randomUUID());
        existing.setUserId(userId);
        existing.setTokenHash(tokenHash);
        existing.setCreatedAt(Instant.now());
        existing.setExpiresAt(Instant.now().plusSeconds(86400));
        existing.setRevoked(false);

        when(repository.findByTokenHash(tokenHash))
                .thenReturn(Optional.of(existing));
        when(repository.save(any(RefreshToken.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        RefreshRotationResult result = service.rotate(rawToken);

        assertNotNull(result);
        assertEquals(userId, result.userId());
        assertNotNull(result.newRefreshToken());
        verify(repository, times(2)).save(any(RefreshToken.class));
    }

    @Test
    void testValidateExpiredTokenThrows() {
        RefreshToken expired = new RefreshToken();
        expired.setId(UUID.randomUUID());
        expired.setUserId(userId);
        expired.setTokenHash(tokenHash);
        expired.setExpiresAt(Instant.now().minusSeconds(1));
        expired.setRevoked(false);

        when(repository.findByTokenHash(tokenHash))
                .thenReturn(Optional.of(expired));

        assertThrows(SecurityException.class, () -> service.validate(rawToken));
    }

    @Test
    void testValidRevokeAllForUser() {
        RefreshToken token1 = new RefreshToken();
        token1.setId(UUID.randomUUID());
        token1.setUserId(userId);
        token1.setRevoked(false);

        when(repository.findAllByUserIdAndRevokedFalse(userId))
                .thenReturn(List.of(token1));
        when(repository.save(any(RefreshToken.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        service.revokeAll(userId);

        verify(repository).findAllByUserIdAndRevokedFalse(userId);
        verify(repository).save(any(RefreshToken.class));
    }
}

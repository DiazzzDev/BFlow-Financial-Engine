package bflow.auth.services;

import bflow.auth.DTO.Record.SyncUserRequest;
import bflow.auth.DTO.Record.SyncUserResponse;
import org.springframework.security.oauth2.jwt.Jwt;

public interface AuthSyncService {
    /**
     * Synchronizes an authenticated Cognito user with the local database.
     *
     * @param jwt authenticated JWT token
     * @param request synchronization request
     * @return synchronization result
     */
    SyncUserResponse synchronize(
            Jwt jwt,
            SyncUserRequest request
    );
}

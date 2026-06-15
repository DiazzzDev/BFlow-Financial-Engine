package bflow.auth.services;

import bflow.auth.DTO.Record.SyncUserRequest;
import bflow.auth.DTO.Record.SyncUserResponse;
import org.springframework.security.oauth2.jwt.Jwt;

public interface AuthSyncService {

    SyncUserResponse synchronize(Jwt jwt);

    SyncUserResponse synchronize(
            Jwt jwt,
            SyncUserRequest request
    );
}
package bflow.auth.DTO.Record;

import java.util.UUID;

public record SyncUserResponse(
        UUID id,
        String email,
        String roles,
        boolean isNewUser
) {}
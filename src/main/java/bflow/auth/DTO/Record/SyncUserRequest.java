package bflow.auth.DTO.Record;

public record SyncUserRequest(
        String idToken,
        String email,
        Boolean emailVerified
) {}
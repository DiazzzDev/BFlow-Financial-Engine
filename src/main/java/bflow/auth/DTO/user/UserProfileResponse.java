package bflow.auth.DTO.user;

import bflow.auth.enums.UserStatus;
import lombok.Builder;
import lombok.Getter;

import java.util.Set;
import java.util.UUID;

@Getter
@Builder
public class UserProfileResponse {

    private UUID id;
    private String email;
    private Set<String> roles;
    private UserStatus status;

}
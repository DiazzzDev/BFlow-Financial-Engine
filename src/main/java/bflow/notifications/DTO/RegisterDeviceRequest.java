package bflow.notifications.DTO;

import bflow.notifications.enums.DevicePlatform;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/** Request to register or refresh a user's push token. */
public record RegisterDeviceRequest(
        @NotBlank
        @Schema(description = "FCM registration token supplied by the client.")
        String token,

        @NotNull
        @Schema(description = "Client platform owning the token.")
        DevicePlatform platform
) { }

package bflow.notifications.enums;

import io.swagger.v3.oas.annotations.media.Schema;

/** Platform where a push notification device token is registered. */
@Schema(description = "Client platform that owns the push token.",
        allowableValues = {"WEB", "ANDROID", "IOS"})
public enum DevicePlatform {
    /** Browser push client. */
    WEB,
    /** Android application. */
    ANDROID,
    /** iOS application. */
    IOS
}

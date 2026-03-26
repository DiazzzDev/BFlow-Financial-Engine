package bflow.notifications.DTO;

import lombok.Getter;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

@Getter @Setter
public class NotificationResponse {
    private UUID id;
    private String title;
    private String message;
    private String type;
    private Boolean read;
    private Instant createdAt;
}

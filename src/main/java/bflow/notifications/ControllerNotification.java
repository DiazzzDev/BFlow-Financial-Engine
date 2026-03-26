package bflow.notifications;

import bflow.common.response.ApiResponse;
import bflow.notifications.DTO.NotificationResponse;
import bflow.notifications.service.NotificationService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/notifications")
@RequiredArgsConstructor
public class ControllerNotification {

    private final NotificationService service;

    @GetMapping
    public ResponseEntity<ApiResponse<List<NotificationResponse>>> getAll(
            Authentication authentication
    ) {

        UUID userId = UUID.fromString(
                (String) authentication.getPrincipal()
        );

        return ResponseEntity.ok(
                ApiResponse.success(
                        "Notifications retrieved",
                        service.getUserNotifications(userId),
                        "/api/v1/notifications"
                )
        );
    }

    @GetMapping("/unread-count")
    public ResponseEntity<ApiResponse<Long>> unreadCount(
            Authentication authentication
    ) {

        UUID userId = UUID.fromString(
                (String) authentication.getPrincipal()
        );

        return ResponseEntity.ok(
                ApiResponse.success(
                        "Unread count retrieved",
                        service.getUnreadCount(userId),
                        "/api/v1/notifications/unread-count"
                )
        );
    }

    @PatchMapping("/{id}/read")
    public ResponseEntity<ApiResponse<Void>> markAsRead(
            @PathVariable UUID id,
            Authentication authentication
    ) {

        UUID userId = UUID.fromString(
                (String) authentication.getPrincipal()
        );

        service.markAsRead(id, userId);

        return ResponseEntity.ok(
                ApiResponse.success(
                        "Notification marked as read",
                        null,
                        "/api/v1/notifications/" + id + "/read"
                )
        );
    }
}
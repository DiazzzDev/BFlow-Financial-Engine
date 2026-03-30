package bflow.recurring;

import bflow.recurring.DTO.RecurringRequest;
import bflow.recurring.DTO.RecurringResponse;
import bflow.recurring.services.RecurringExecutionService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/recurring")
@RequiredArgsConstructor
public class ControllerRecurring {

    private final RecurringExecutionService recurringService;

    @PostMapping
    public RecurringResponse create(
            @RequestBody RecurringRequest request,
            Authentication authentication
    ) {
        UUID userId = UUID.fromString(authentication.getName());
        return recurringService.createRecurring(request, userId);
    }

    @GetMapping
    public List<RecurringResponse> getUserRecurring(
            Authentication authentication
    ) {
        UUID userId = UUID.fromString(authentication.getName());
        return recurringService.getUserRecurring(userId);
    }

    @PatchMapping("/{id}/activate")
    public void activate(
            @PathVariable UUID id,
            Authentication authentication
    ) {
        UUID userId = UUID.fromString(authentication.getName());
        recurringService.toggleRecurring(id, userId, true);
    }

    @PatchMapping("/{id}/deactivate")
    public void deactivate(
            @PathVariable UUID id,
            Authentication authentication
    ) {
        UUID userId = UUID.fromString(authentication.getName());
        recurringService.toggleRecurring(id, userId, false);
    }

    @DeleteMapping("/{id}")
    public void delete(
            @PathVariable UUID id,
            Authentication authentication
    ) {
        UUID userId = UUID.fromString(authentication.getName());
        recurringService.deleteRecurring(id, userId);
    }
}

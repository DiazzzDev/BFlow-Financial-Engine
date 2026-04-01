package bflow.recurring;

import bflow.recurring.DTO.RecurringRequest;
import bflow.recurring.DTO.RecurringResponse;
import bflow.recurring.services.RecurringExecutionService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/**
 * REST controller for managing recurring transactions.
 */
@RestController
@RequestMapping("/api/v1/recurring")
@RequiredArgsConstructor
public class ControllerRecurring {

    /**
     * The recurring execution service.
     */
    private final RecurringExecutionService recurringService;

    /**
     * Create a new recurring transaction.
     *
     * @param request the recurring request
     * @param authentication the authentication object
     * @return the created recurring response
     */
    @PostMapping
    public RecurringResponse create(
            @RequestBody final RecurringRequest request,
            final Authentication authentication
    ) {
        UUID userId = UUID.fromString(authentication.getName());
        return recurringService.createRecurring(request, userId);
    }

    /**
     * Get all recurring transactions for the authenticated user.
     *
     * @param authentication the authentication object
     * @return list of recurring responses
     */
    @GetMapping
    public List<RecurringResponse> getUserRecurring(
            final Authentication authentication
    ) {
        UUID userId = UUID.fromString(authentication.getName());
        return recurringService.getUserRecurring(userId);
    }

    /**
     * Activate a recurring transaction.
     *
     * @param id the recurring transaction ID
     * @param authentication the authentication object
     */
    @PatchMapping("/{id}/activate")
    public void activate(
            @PathVariable final UUID id,
            final Authentication authentication
    ) {
        UUID userId = UUID.fromString(authentication.getName());
        recurringService.toggleRecurring(id, userId, true);
    }

    /**
     * Deactivate a recurring transaction.
     *
     * @param id the recurring transaction ID
     * @param authentication the authentication object
     */
    @PatchMapping("/{id}/deactivate")
    public void deactivate(
            @PathVariable final UUID id,
            final Authentication authentication
    ) {
        UUID userId = UUID.fromString(authentication.getName());
        recurringService.toggleRecurring(id, userId, false);
    }

    /**
     * Delete a recurring transaction.
     *
     * @param id the recurring transaction ID
     * @param authentication the authentication object
     */
    @DeleteMapping("/{id}")
    public void delete(
            @PathVariable final UUID id,
            final Authentication authentication
    ) {
        UUID userId = UUID.fromString(authentication.getName());
        recurringService.deleteRecurring(id, userId);
    }
}

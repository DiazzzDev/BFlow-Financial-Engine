package bflow.subscription.controllers;

import bflow.common.i18n.MessageService;
import bflow.common.response.ApiResponse;
import bflow.subscription.dto.PlanResponse;
import bflow.subscription.services.PlanService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/** Public, frontend-consumable subscription plan catalogue. */
@Tag(name = "Plans", description = "Public subscription plan catalogue")
@RestController
@RequestMapping("/api/v1/plans")
@RequiredArgsConstructor
public final class PlanController {

    private final PlanService planService;
    private final MessageService messageService;

    /**
     * Retrieves all currently available plans and their configured benefits.
     *
     * @param request incoming request
     * @return the active plan catalogue
     */
    @Operation(summary = "List active plans with every benefit")
    @GetMapping
    public ApiResponse<List<PlanResponse>> getPlans(
            final HttpServletRequest request
    ) {
        return ApiResponse.success(
                messageService.get("subscription.plan.list.retrieved"),
                planService.getActivePlans(),
                request.getRequestURI()
        );
    }

    /**
     * Retrieves one available plan and all of its configured benefits.
     *
     * @param id plan identifier
     * @param request incoming request
     * @return the plan catalogue entry
     */
    @Operation(summary = "Get an active plan with every benefit")
    @GetMapping("/{id}")
    public ApiResponse<PlanResponse> getPlanById(
            @PathVariable final UUID id,
            final HttpServletRequest request
    ) {
        return ApiResponse.success(
                messageService.get("subscription.plan.retrieved"),
                planService.getActivePlan(id),
                request.getRequestURI()
        );
    }
}

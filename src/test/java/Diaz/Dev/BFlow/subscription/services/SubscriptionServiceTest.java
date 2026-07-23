package Diaz.Dev.BFlow.subscription.services;

import bflow.auth.entities.User;
import bflow.subscription.WompiApiClient;
import bflow.subscription.entities.Plan;
import bflow.subscription.entities.Subscription;
import bflow.subscription.enums.SubscriptionStatus;
import bflow.subscription.repository.RepositorySubscription;
import bflow.subscription.services.PlanService;
import bflow.subscription.services.SubscriptionService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SubscriptionServiceTest {

    @Mock private RepositorySubscription repositorySubscription;
    @Mock private PlanService planService;
    @Mock private WompiApiClient wompiApiClient;

    @InjectMocks private SubscriptionService service;

    private Subscription subscriptionWith(String planCode, SubscriptionStatus status, UUID userId, String linkId) {
        Plan plan = new Plan();
        plan.setCode(planCode);
        User user = new User();
        user.setId(userId);
        Subscription subscription = new Subscription();
        subscription.setId(UUID.randomUUID());
        subscription.setPlan(plan);
        subscription.setUser(user);
        subscription.setStatus(status);
        subscription.setProviderLinkId(linkId);
        return subscription;
    }

    // NOTA: reemplaza el constructor según cómo quede finalmente SubscriptionService
    // (necesita wompiApiClient inyectado tras el cambio de cancel()).

    @Test
    void noPermiteCancelarElPlanFree() {
        UUID userId = UUID.randomUUID();
        Subscription free = subscriptionWith("FREE", SubscriptionStatus.ACTIVE, userId, null);
        when(repositorySubscription.findById(free.getId())).thenReturn(Optional.of(free));

        // service = new SubscriptionService(repositorySubscription, planService, wompiApiClient);
        assertThatThrownBy(() -> service.cancel(userId, free.getId()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("gratuito");

        verify(repositorySubscription, never()).save(any());
    }

    @Test
    void noPermiteCancelarUnaSuscripcionYaExpirada() {
        UUID userId = UUID.randomUUID();
        Subscription expired = subscriptionWith("PRO_MONTHLY", SubscriptionStatus.EXPIRED, userId, "link-1");
        when(repositorySubscription.findById(expired.getId())).thenReturn(Optional.of(expired));

        assertThatThrownBy(() -> service.cancel(userId, expired.getId()))
                .isInstanceOf(IllegalStateException.class);

        verify(wompiApiClient, never()).deactivateRecurringLink(any());
    }

    @Test
    void cancelarDosVecesEsIdempotente() {
        UUID userId = UUID.randomUUID();
        Subscription canceled = subscriptionWith("PRO_MONTHLY", SubscriptionStatus.CANCELED, userId, "link-1");
        when(repositorySubscription.findById(canceled.getId())).thenReturn(Optional.of(canceled));

        service.cancel(userId, canceled.getId());

        verify(repositorySubscription, never()).save(any());
        verify(wompiApiClient, never()).deactivateRecurringLink(any());
    }

    @Test
    void noPermiteCancelarSuscripcionDeOtroUsuario() {
        UUID owner = UUID.randomUUID();
        UUID attacker = UUID.randomUUID();
        Subscription subscription = subscriptionWith("PRO_MONTHLY", SubscriptionStatus.ACTIVE, owner, "link-1");
        when(repositorySubscription.findById(subscription.getId())).thenReturn(Optional.of(subscription));

        assertThatThrownBy(() -> service.cancel(attacker, subscription.getId()))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void cancelaLocalmenteAunqueWompiFalle() {
        UUID userId = UUID.randomUUID();
        Subscription subscription = subscriptionWith("PRO_MONTHLY", SubscriptionStatus.ACTIVE, userId, "link-1");
        when(repositorySubscription.findById(subscription.getId())).thenReturn(Optional.of(subscription));
        doThrow(new RuntimeException("Wompi caído")).when(wompiApiClient).deactivateRecurringLink("link-1");

        service.cancel(userId, subscription.getId());

        assertThat(subscription.getStatus()).isEqualTo(SubscriptionStatus.CANCELED);
    }
}

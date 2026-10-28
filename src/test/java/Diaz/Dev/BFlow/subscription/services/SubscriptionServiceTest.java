package Diaz.Dev.BFlow.subscription.services;

import bflow.auth.entities.User;
import bflow.common.i18n.MessageService;
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
import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SubscriptionServiceTest {

    @Mock private RepositorySubscription repositorySubscription;
    @Mock private PlanService planService;
    @Mock private WompiApiClient wompiApiClient;
    @Mock private MessageService messageService;

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
        when(messageService.get(eq("subscription.freePlan.cannotCancel")))
                .thenReturn("El plan gratuito no se puede cancelar");

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

    @Test
    void cancelaSuscripcionActivaDesactivandoElEnlaceYDeshabilitandoRenovacion() {
        UUID userId = UUID.randomUUID();
        Subscription active = subscriptionWith(
                "PRO_MONTHLY", SubscriptionStatus.ACTIVE, userId, "link-2");
        when(repositorySubscription.findById(active.getId()))
                .thenReturn(Optional.of(active));

        service.cancel(userId, active.getId());

        assertThat(active.getStatus()).isEqualTo(SubscriptionStatus.CANCELED);
        assertThat(active.isAutoRenew()).isFalse();
        assertThat(active.getCanceledAt()).isNotNull();
        assertThat(active.getEndsAt()).isNotNull();
        assertThat(active.getNextBillingAt()).isNull();
        verify(wompiApiClient).deactivateRecurringLink("link-2");
        verify(repositorySubscription).save(active);
    }

    @Test
    void creaSuscripcionFreeCuandoElUsuarioNoTieneNingunaActiva() {
        UUID userId = UUID.randomUUID();
        User user = new User();
        user.setId(userId);
        Plan freePlan = new Plan();
        freePlan.setCode("FREE");
        freePlan.setPrice(BigDecimal.ZERO);
        when(repositorySubscription.findByUserIdAndStatus(
                userId, SubscriptionStatus.ACTIVE)).thenReturn(Optional.empty());
        when(planService.getFreePlan()).thenReturn(freePlan);

        service.createFreeSubscription(user);

        var saved = org.mockito.ArgumentCaptor.forClass(Subscription.class);
        verify(repositorySubscription).save(saved.capture());
        assertThat(saved.getValue().getUser()).isSameAs(user);
        assertThat(saved.getValue().getPlan()).isSameAs(freePlan);
        assertThat(saved.getValue().getStatus()).isEqualTo(SubscriptionStatus.ACTIVE);
        assertThat(saved.getValue().getBillingAmount()).isEqualByComparingTo("0");
        assertThat(saved.getValue().getStartsAt()).isNotNull();
        assertThat(saved.getValue().isAutoRenew()).isFalse();
    }

    @Test
    void omiteCreacionFreeSiYaExisteUnaSuscripcionActiva() {
        UUID userId = UUID.randomUUID();
        User user = new User();
        user.setId(userId);
        when(repositorySubscription.findByUserIdAndStatus(
                userId, SubscriptionStatus.ACTIVE))
                .thenReturn(Optional.of(new Subscription()));

        service.createFreeSubscription(user);

        verifyNoInteractions(planService);
        verify(repositorySubscription, never()).save(any());
    }

    @Test
    void createFreeSubscription_ignoresConcurrentDuplicateCreation() {
        UUID userId = UUID.randomUUID();
        User user = new User();
        user.setId(userId);
        Plan freePlan = new Plan();
        freePlan.setCode("FREE");
        freePlan.setPrice(BigDecimal.ZERO);
        when(repositorySubscription.findByUserIdAndStatus(
                userId, SubscriptionStatus.ACTIVE)).thenReturn(Optional.empty());
        when(planService.getFreePlan()).thenReturn(freePlan);
        when(repositorySubscription.save(any(Subscription.class)))
                .thenThrow(new org.springframework.dao.DataIntegrityViolationException(
                        "duplicate subscription"));

        org.assertj.core.api.Assertions.assertThatCode(
                () -> service.createFreeSubscription(user))
                .doesNotThrowAnyException();
    }

    @Test
    void findMySubscriptions_returnsEmptyListWithoutFallbackData() {
        UUID userId = UUID.randomUUID();
        when(repositorySubscription.findAllByUserIdOrderByCreatedAtDesc(userId))
                .thenReturn(List.of());

        assertThat(service.findMySubscriptions(userId)).isEmpty();
    }

    @Test
    void findMySubscriptions_mapsOwnedSubscriptionsInRepositoryOrder() {
        UUID userId = UUID.randomUUID();
        Plan plan = new Plan();
        plan.setName("Pro");
        Subscription active = subscriptionWith(
                "PRO_MONTHLY", SubscriptionStatus.ACTIVE, userId, null);
        active.setPlan(plan);
        active.setBillingAmount(new BigDecimal("9.99"));
        active.setStartsAt(java.time.Instant.parse("2026-10-01T00:00:00Z"));
        when(repositorySubscription.findAllByUserIdOrderByCreatedAtDesc(userId))
                .thenReturn(List.of(active));

        var responses = service.findMySubscriptions(userId);

        assertThat(responses).hasSize(1);
        assertThat(responses.getFirst().planName()).isEqualTo("Pro");
        assertThat(responses.getFirst().status()).isEqualTo(SubscriptionStatus.ACTIVE);
        assertThat(responses.getFirst().billingAmount())
                .isEqualByComparingTo("9.99");
    }
}

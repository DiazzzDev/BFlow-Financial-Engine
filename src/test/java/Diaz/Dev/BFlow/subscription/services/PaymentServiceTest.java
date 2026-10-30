package Diaz.Dev.BFlow.subscription.services;

import bflow.auth.entities.User;
import bflow.auth.repository.RepositoryUser;
import bflow.common.i18n.MessageService;
import bflow.subscription.WompiApiClient;
import bflow.subscription.dto.CheckoutRequest;
import bflow.subscription.entities.Plan;
import bflow.subscription.entities.Feature;
import bflow.subscription.entities.PlanFeature;
import bflow.subscription.entities.Subscription;
import bflow.subscription.enums.BillingPeriod;
import bflow.subscription.enums.SubscriptionStatus;
import bflow.subscription.repository.RepositoryPlan;
import bflow.subscription.repository.RepositoryPlanFeature;
import bflow.subscription.repository.RepositorySubscription;
import bflow.subscription.services.PaymentService;
import bflow.subscription.services.PlanCheckoutPresentation;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PaymentServiceTest {

    @Mock private RepositoryPlan repositoryPlan;
    @Mock private RepositoryPlanFeature repositoryPlanFeature;
    @Mock private RepositorySubscription repositorySubscription;
    @Mock private RepositoryUser repositoryUser;
    @Mock private WompiApiClient wompiApiClient;
    @Mock private MessageService messageService;

    private PaymentService service;
    private UUID userId;
    private Plan proMonthly;

    @BeforeEach
    void setUp() {
        service = new PaymentService(repositoryPlan, repositorySubscription,
                repositoryUser, wompiApiClient, messageService,
                new PlanCheckoutPresentation(repositoryPlanFeature));
        userId = UUID.randomUUID();
        proMonthly = proMonthlyPlan();
        User user = new User();
        user.setId(userId);
        when(repositoryPlan.findById(proMonthly.getId()))
                .thenReturn(Optional.of(proMonthly));
        lenient().when(repositorySubscription.existsByUserIdAndPlanIdAndStatusIn(
                eq(userId), eq(proMonthly.getId()), any())).thenReturn(false);
        lenient().when(repositoryUser.getReferenceById(userId)).thenReturn(user);
        lenient().when(repositoryPlanFeature.findByPlanId(proMonthly.getId()))
                .thenReturn(proMonthlyFeatures());
    }

    @Test
    void monthlyCheckoutUsesNaturalPlanDescriptionAndAttachesBrandImage() {
        when(wompiApiClient.createRecurringLink(any(Integer.class), any(),
                eq(new BigDecimal("9.99")), any()))
                .thenReturn(new WompiApiClient.RecurringLinkResponse(
                        "link-123", "https://s.wompi.sv/link-123"));

        var response = service.createCheckout(userId,
                new CheckoutRequest(proMonthly.getId()));

        ArgumentCaptor<String> name = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> description =
                ArgumentCaptor.forClass(String.class);
        verify(wompiApiClient).createRecurringLink(any(Integer.class),
                name.capture(), eq(new BigDecimal("9.99")), description.capture());
        verify(wompiApiClient).attachRecurringLinkImage("link-123");
        assertThat(name.getValue())
                .isEqualTo("BFlow Pro Plan — suscripción mensual");
        assertThat(description.getValue())
                .contains("hasta 100 billeteras")
                .contains("hasta 25 transacciones recurrentes")
                .contains("exportar tus datos");
        assertThat(response.checkoutUrl()).isEqualTo("https://s.wompi.sv/link-123");
    }

    @Test
    void activeSubscriptionCanPrepareAnotherWompiCheckoutBeforeItEnds() {
        when(wompiApiClient.createRecurringLink(any(Integer.class), any(),
                eq(new BigDecimal("9.99")), any()))
                .thenReturn(new WompiApiClient.RecurringLinkResponse(
                        "renewal-link", "https://s.wompi.sv/renewal"));

        var checkout = service.createCheckout(userId,
                new CheckoutRequest(proMonthly.getId()));

        ArgumentCaptor<List<SubscriptionStatus>> statuses =
                ArgumentCaptor.forClass(List.class);
        verify(repositorySubscription).existsByUserIdAndPlanIdAndStatusIn(
                eq(userId), eq(proMonthly.getId()), statuses.capture());
        assertThat(statuses.getValue())
                .containsExactly(SubscriptionStatus.PENDING_ACTIVATION,
                        SubscriptionStatus.PAST_DUE)
                .doesNotContain(SubscriptionStatus.ACTIVE);
        ArgumentCaptor<Subscription> pending =
                ArgumentCaptor.forClass(Subscription.class);
        verify(repositorySubscription).saveAndFlush(pending.capture());
        assertThat(pending.getValue().getStatus())
                .isEqualTo(SubscriptionStatus.PENDING_ACTIVATION);
        assertThat(checkout.checkoutUrl()).isEqualTo("https://s.wompi.sv/renewal");
    }

    @Test
    void duplicatePendingCheckoutIsRejectedWithoutCreatingAnotherWompiLink() {
        when(repositorySubscription.existsByUserIdAndPlanIdAndStatusIn(
                eq(userId), eq(proMonthly.getId()), any())).thenReturn(true);
        when(messageService.get("subscription.alreadyActiveOrPending"))
                .thenReturn("A checkout is already active or pending");

        org.assertj.core.api.Assertions.assertThatThrownBy(() ->
                service.createCheckout(userId,
                        new CheckoutRequest(proMonthly.getId())))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("already active or pending");

        verify(wompiApiClient, never()).createRecurringLink(anyInt(), any(),
                any(), any());
        verify(repositorySubscription, never()).saveAndFlush(any());
    }

    @Test
    void annualRenewalCheckoutIsPendingUntilOneTimePaymentIsConfirmed() {
        proMonthly.setBillingPeriod(BillingPeriod.YEARLY);
        when(wompiApiClient.createPaymentLink(any(), eq(new BigDecimal("9.99")),
                any())).thenReturn(new WompiApiClient.PaymentLinkResponse(
                        101L, "qr", "https://wompi.example/annual", false));

        var checkout = service.createCheckout(userId,
                new CheckoutRequest(proMonthly.getId()));

        ArgumentCaptor<Subscription> pending =
                ArgumentCaptor.forClass(Subscription.class);
        verify(repositorySubscription).saveAndFlush(pending.capture());
        assertThat(pending.getValue().getStatus())
                .isEqualTo(SubscriptionStatus.PENDING_ACTIVATION);
        assertThat(pending.getValue().isAutoRenew()).isFalse();
        assertThat(pending.getValue().getCheckoutReference()).isNotBlank();
        assertThat(checkout.checkoutUrl())
                .isEqualTo("https://wompi.example/annual");
        verify(wompiApiClient, never()).attachRecurringLinkImage(any());
    }

    @Test
    void renewalCheckoutCreatesANewOneTimeWompiLinkWhenNoneIsPending() {
        proMonthly.setBillingPeriod(BillingPeriod.YEARLY);
        when(repositorySubscription
                .findFirstByUserIdAndPlanIdAndStatusOrderByCreatedAtDesc(
                        userId, proMonthly.getId(),
                        SubscriptionStatus.PENDING_ACTIVATION))
                .thenReturn(Optional.empty());
        when(wompiApiClient.createPaymentLink(any(),
                eq(new BigDecimal("9.99")), any()))
                .thenReturn(new WompiApiClient.PaymentLinkResponse(
                        202L, "qr", "https://wompi.example/annual-renewal", false));

        var checkout = service.createRenewalCheckout(userId, proMonthly.getId());

        assertThat(checkout.checkoutUrl())
                .isEqualTo("https://wompi.example/annual-renewal");
        ArgumentCaptor<Subscription> saved =
                ArgumentCaptor.forClass(Subscription.class);
        verify(repositorySubscription).saveAndFlush(saved.capture());
        assertThat(saved.getValue().getCheckoutReference()).isNotBlank();
        assertThat(saved.getValue().getCheckoutUrl())
                .isEqualTo("https://wompi.example/annual-renewal");
    }

    @Test
    void renewalCheckoutReusesOnlyThePendingRenewalLinkNotTheActivePlanUrl() {
        proMonthly.setBillingPeriod(BillingPeriod.YEARLY);
        Subscription pending = new Subscription();
        pending.setId(UUID.randomUUID());
        pending.setStatus(SubscriptionStatus.PENDING_ACTIVATION);
        pending.setCheckoutReference("new-unique-reference");
        pending.setCheckoutUrl("https://wompi.example/fresh-pending-link");
        when(repositorySubscription
                .findFirstByUserIdAndPlanIdAndStatusOrderByCreatedAtDesc(
                        userId, proMonthly.getId(),
                        SubscriptionStatus.PENDING_ACTIVATION))
                .thenReturn(Optional.of(pending));

        var checkout = service.createRenewalCheckout(userId, proMonthly.getId());

        assertThat(checkout.subscriptionId()).isEqualTo(pending.getId());
        assertThat(checkout.checkoutUrl())
                .isEqualTo("https://wompi.example/fresh-pending-link");
        verify(wompiApiClient, never()).createPaymentLink(any(), any(), any());
    }

    private Plan proMonthlyPlan() {
        Plan plan = new Plan();
        plan.setId(UUID.randomUUID());
        plan.setCode("PRO_MONTHLY");
        plan.setName("Pro Plan");
        plan.setPrice(new BigDecimal("9.99"));
        plan.setBillingPeriod(BillingPeriod.MONTHLY);
        return plan;
    }

    private List<PlanFeature> proMonthlyFeatures() {
        return List.of(
                planFeature("WALLETS", 100),
                planFeature("BUDGETS", 100),
                planFeature("RECURRING_TRANSACTIONS", 25),
                planFeature("SHARED_WALLETS", 100),
                planFeature("WALLET_MEMBERS", 10),
                planFeature("CAN_CREATE_SHARED_WALLETS", null),
                planFeature("EXPORT", null),
                planFeature("DASHBOARD_CUSTOMIZATION", null)
        );
    }

    private PlanFeature planFeature(String code, Integer limit) {
        Feature feature = new Feature();
        feature.setCode(code);
        PlanFeature planFeature = new PlanFeature();
        planFeature.setFeature(feature);
        planFeature.setLimit(limit);
        planFeature.setEnabled(true);
        return planFeature;
    }
}

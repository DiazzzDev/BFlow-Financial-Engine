package Diaz.Dev.BFlow.subscription.services;

import bflow.auth.entities.User;
import bflow.auth.repository.RepositoryUser;
import bflow.common.i18n.MessageService;
import bflow.subscription.WompiApiClient;
import bflow.subscription.dto.CheckoutRequest;
import bflow.subscription.entities.Plan;
import bflow.subscription.entities.Feature;
import bflow.subscription.entities.PlanFeature;
import bflow.subscription.enums.BillingPeriod;
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
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
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
        when(repositorySubscription.existsByUserIdAndPlanIdAndStatusIn(
                eq(userId), eq(proMonthly.getId()), any())).thenReturn(false);
        when(repositoryUser.getReferenceById(userId)).thenReturn(user);
        when(repositoryPlanFeature.findByPlanId(proMonthly.getId()))
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

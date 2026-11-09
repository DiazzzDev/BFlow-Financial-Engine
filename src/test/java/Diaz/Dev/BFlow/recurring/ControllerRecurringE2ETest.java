package Diaz.Dev.BFlow.recurring;

import bflow.auth.services.CurrentUserService;
import bflow.common.exception.GlobalExceptionHandler;
import bflow.common.i18n.LocaleConfig;
import bflow.recurring.ControllerRecurring;
import bflow.recurring.DTO.RecurringResponse;
import bflow.recurring.enums.RecurringFrequency;
import bflow.recurring.enums.RecurringType;
import bflow.recurring.services.RecurringExecutionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Exercises the recurring wallet endpoint and optional-end-date PUT contract over HTTP. */
class ControllerRecurringE2ETest {

    private final RecurringExecutionService recurringService =
            mock(RecurringExecutionService.class);
    private final CurrentUserService currentUserService =
            mock(CurrentUserService.class);
    private MockMvc mockMvc;
    private final UUID userId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        LocalValidatorFactoryBean validator = new LocalValidatorFactoryBean();
        validator.setValidationMessageSource(new LocaleConfig().messageSource());
        validator.afterPropertiesSet();
        mockMvc = MockMvcBuilders.standaloneSetup(
                        new ControllerRecurring(recurringService, currentUserService))
                .setValidator(validator)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
        when(currentUserService.getCurrentUserId(any())).thenReturn(userId);
    }

    @Test
    void getWalletRecurrencesUsesAuthenticatedUserAndReturnsList() throws Exception {
        UUID walletId = UUID.randomUUID();
        when(recurringService.getWalletRecurring(walletId, userId))
                .thenReturn(List.of());

        mockMvc.perform(get("/api/v1/recurring/wallet/{walletId}", walletId)
                        .principal(new TestingAuthenticationToken("user", "")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$.length()").value(0));

        verify(recurringService).getWalletRecurring(walletId, userId);
    }

    @Test
    void getRecurrenceByIdUsesAuthenticatedUser() throws Exception {
        UUID recurrenceId = UUID.randomUUID();
        RecurringResponse response = new RecurringResponse();
        response.setId(recurrenceId);
        response.setTitle("Salary");
        response.setEndDate(null);
        when(recurringService.getRecurringById(recurrenceId, userId))
                .thenReturn(response);

        mockMvc.perform(get("/api/v1/recurring/{id}", recurrenceId)
                        .principal(new TestingAuthenticationToken("user", "")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(recurrenceId.toString()))
                .andExpect(jsonPath("$.title").value("Salary"));

        verify(recurringService).getRecurringById(recurrenceId, userId);
    }

    @Test
    void putAllowsIndefiniteSalaryWhenEndDateIsOmitted() throws Exception {
        UUID recurrenceId = UUID.randomUUID();
        UUID walletId = UUID.randomUUID();
        UUID categoryId = UUID.randomUUID();
        RecurringResponse response = new RecurringResponse();
        response.setId(recurrenceId);
        response.setTitle("Salary");
        response.setAmount(new BigDecimal("2000.00"));
        response.setType(RecurringType.INCOME);
        response.setFrequency(RecurringFrequency.MONTHLY);
        response.setIntervalValue(1);
        response.setStartDate(LocalDate.of(2026, 1, 1));
        response.setEndDate(null);
        response.setNextExecutionDate(LocalDate.of(2026, 11, 1));
        response.setActive(true);
        response.setWalletId(walletId);
        response.setCategoryId(categoryId);
        when(recurringService.updateRecurring(eq(recurrenceId), any(), eq(userId)))
                .thenReturn(response);

        mockMvc.perform(put("/api/v1/recurring/{id}", recurrenceId)
                        .principal(new TestingAuthenticationToken("user", ""))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "title":"Salary",
                                  "amount":2000.00,
                                  "walletId":"%s",
                                  "categoryId":"%s",
                                  "type":"INCOME",
                                  "frequency":"MONTHLY",
                                  "startDate":"2026-01-01"
                                }
                                """.formatted(walletId, categoryId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("Salary"))
                .andExpect(jsonPath("$.endDate").doesNotExist());

        verify(recurringService).updateRecurring(eq(recurrenceId), any(), eq(userId));
    }

    @Test
    void putRejectsEndDateBeforeStartDate() throws Exception {
        UUID recurrenceId = UUID.randomUUID();
        mockMvc.perform(put("/api/v1/recurring/{id}", recurrenceId)
                        .principal(new TestingAuthenticationToken("user", ""))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "title":"Salary",
                                  "amount":2000.00,
                                  "walletId":"%s",
                                  "categoryId":"%s",
                                  "type":"INCOME",
                                  "frequency":"MONTHLY",
                                  "startDate":"2026-01-01",
                                  "endDate":"2025-12-31"
                                }
                                """.formatted(UUID.randomUUID(), UUID.randomUUID())))
                .andExpect(status().isBadRequest());
    }
}

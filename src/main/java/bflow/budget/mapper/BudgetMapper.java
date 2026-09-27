package bflow.budget.mapper;

import bflow.budget.DTO.BudgetResponse;
import bflow.budget.entity.Budget;
import bflow.budget.enums.BudgetStatus;
import bflow.common.mapper.BaseMapperConfig;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import java.math.BigDecimal;

/** Maps budget entities and calculated values to API responses. */
@Mapper(config = BaseMapperConfig.class)
public interface BudgetMapper {

    /** Maps persisted budget metadata without recalculating amounts. */
    @Mapping(target = "id", source = "budget.id")
    @Mapping(target = "walletId", source = "budget.wallet.id")
    @Mapping(target = "walletName", source = "budget.wallet.name")
    @Mapping(target = "categoryId", source = "budget.category.id")
    @Mapping(target = "categoryName", source = "budget.category.name")
    @Mapping(target = "budgetLimit", source = "budget.amount")
    @Mapping(target = "status", source = "budget.lastAlertStatus")
    BudgetResponse toResponse(Budget budget);

    /** Maps a budget with its calculated current-period values. */
    @Mapping(target = "id", source = "budget.id")
    @Mapping(target = "walletId", source = "budget.wallet.id")
    @Mapping(target = "walletName", source = "budget.wallet.name")
    @Mapping(target = "categoryId", source = "budget.category.id")
    @Mapping(target = "categoryName", source = "budget.category.name")
    @Mapping(target = "budgetLimit", source = "budget.amount")
    @Mapping(target = "scope", source = "budget.scope")
    @Mapping(target = "period", source = "budget.period")
    @Mapping(target = "startDate", source = "budget.startDate")
    @Mapping(target = "thresholdWarning", source = "budget.thresholdWarning")
    @Mapping(target = "thresholdCritical", source = "budget.thresholdCritical")
    @Mapping(target = "createdAt", source = "budget.createdAt")
    @Mapping(target = "status", source = "status")
    BudgetResponse toCalculatedResponse(
            Budget budget,
            BigDecimal spent,
            BigDecimal remaining,
            Integer percentage,
            BudgetStatus status
    );
}

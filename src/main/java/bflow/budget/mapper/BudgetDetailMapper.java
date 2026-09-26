package bflow.budget.mapper;

import bflow.budget.DTO.BudgetDetailResponse;
import bflow.common.mapper.BaseMapperConfig;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

/** Maps calculated budget detail views to API responses. */
@Mapper(config = BaseMapperConfig.class)
public interface BudgetDetailMapper {

    /**
     * Assembles the persisted budget metadata and calculated values into the
     * detail response.
     *
     * @param view mapping context prepared by the budget service
     * @return budget detail response
     */
    @Mapping(target = "id", source = "view.budget.id")
    @Mapping(target = "walletId", source = "view.budget.wallet.id")
    @Mapping(target = "walletName", source = "view.budget.wallet.name")
    @Mapping(target = "currency", source = "view.currency")
    @Mapping(target = "categoryId", source = "view.budget.category.id")
    @Mapping(target = "categoryName", source = "view.budget.category.name")
    @Mapping(target = "scope", source = "view.budget.scope")
    @Mapping(target = "period", source = "view.budget.period")
    @Mapping(target = "status", source = "view.status")
    @Mapping(target = "startDate", source = "view.startDate")
    @Mapping(target = "endDate", source = "view.endDate")
    @Mapping(target = "daysLeft", source = "view.daysLeft")
    @Mapping(target = "daysElapsed", source = "view.daysElapsed")
    @Mapping(target = "budgetLimit", source = "view.budgetLimit")
    @Mapping(target = "spent", source = "view.spent")
    @Mapping(target = "remaining", source = "view.remaining")
    @Mapping(target = "percentage", source = "view.percentage")
    @Mapping(target = "thresholdWarning", source = "view.thresholdWarning")
    @Mapping(target = "thresholdCritical", source = "view.thresholdCritical")
    @Mapping(target = "transactionCount", source = "view.transactionCount")
    @Mapping(target = "averageDailySpend", source = "view.averageDailySpend")
    @Mapping(target = "projectedTotal", source = "view.projectedTotal")
    @Mapping(target = "spendingTrend", source = "view.spendingTrend")
    @Mapping(target = "recentActivity", source = "view.recentActivity")
    BudgetDetailResponse toResponse(BudgetDetailView view);
}

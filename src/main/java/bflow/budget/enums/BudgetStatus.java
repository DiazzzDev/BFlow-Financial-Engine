package bflow.budget.enums;

public enum BudgetStatus {
    OK,         // below the umbral warning
    WARNING,    // above thresholdWarning
    CRITICAL,   // exceeded thresholdCritical
    EXCEEDED    // exceeded the budget (100%)
}

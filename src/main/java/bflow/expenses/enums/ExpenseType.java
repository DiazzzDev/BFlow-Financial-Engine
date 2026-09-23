package bflow.expenses.enums;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Enumeration of expense categories.
 * Used to classify expense transactions in the financial system.
 */
@Schema(description = "Legacy expense classification.", allowableValues = {
        "TRANSPORTATION", "HEALTHCARE", "INSURANCE", "ENTERTAINMENT",
        "FOOD", "TRAVEL", "SHOPPING", "SUBSCRIPTIONS", "DEBT_PAYMENT",
        "LOAN_REPAYMENT", "TAX_PAYMENT", "INVESTMENT_CONTRIBUTION",
        "EDUCATION", "PET_CARE", "GIFTS_DONATIONS", "OTHER"})
public enum ExpenseType {
    /** Transportation-related expenses (gas, public transit, etc.). */
    TRANSPORTATION,
    /** Healthcare-related expenses (medical, dental, etc.). */
    HEALTHCARE,
    /** Insurance expenses (car, home, health, life, etc.). */
    INSURANCE,
    /** Entertainment expenses (movies, games, hobbies, etc.). */
    ENTERTAINMENT,
    /** Food and dining expenses (groceries, restaurants, etc.). */
    FOOD,
    /** Travel-related expenses (flights, hotels, lodging, etc.). */
    TRAVEL,
    /** Shopping and retail expenses (clothes, household items, etc.). */
    SHOPPING,
    /** Subscription and membership expenses. */
    SUBSCRIPTIONS,
    /** Debt repayment expenses. */
    DEBT_PAYMENT,
    /** Loan repayment expenses. */
    LOAN_REPAYMENT,
    /** Tax payment expenses. */
    TAX_PAYMENT,
    /** Investment contribution expenses. */
    INVESTMENT_CONTRIBUTION,
    /** Education and training expenses. */
    EDUCATION,
    /** Pet care expenses (veterinary, food, grooming, etc.). */
    PET_CARE,
    /** Gifts and charitable donations. */
    GIFTS_DONATIONS,
    /** Other miscellaneous expenses. */
    OTHER
}

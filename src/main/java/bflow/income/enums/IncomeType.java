package bflow.income.enums;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Enumeration of different income types.
 */
@Schema(description = "Legacy income classification.", allowableValues = {
        "SALARY", "BONUS", "COMMISSION", "FREELANCE", "BUSINESS_PROFIT",
        "INVESTMENT_RETURN", "INTEREST", "RENTAL_INCOME", "ROYALTIES",
        "SIDE_HUSTLE", "GIFT", "REFUND", "CASHBACK", "GOVERNMENT_BENEFIT",
        "SCHOLARSHIP", "INSURANCE_PAYOUT", "LOAN_RECEIVED", "OTHER"})
public enum IncomeType {

    /**
     * Fixed employment payment.
     */
    SALARY,

    /**
     * Performance or annual bonus.
     */
    BONUS,

    /**
     * Sales-based earnings.
     */
    COMMISSION,

    /**
     * Independent contractor income.
     */
    FREELANCE,

    /**
     * Profit from owned business.
     */
    BUSINESS_PROFIT,

    /**
     * Dividends, capital gains from investments.
     */
    INVESTMENT_RETURN,

    /**
     * Bank or financial interest.
     */
    INTEREST,

    /**
     * Property rent earnings.
     */
    RENTAL_INCOME,

    /**
     * Intellectual property income.
     */
    ROYALTIES,

    /**
     * Secondary informal income.
     */
    SIDE_HUSTLE,

    /**
     * Money received as a gift.
     */
    GIFT,

    /**
     * Tax or product refund.
     */
    REFUND,

    /**
     * Cashback rewards.
     */
    CASHBACK,

    /**
     * Subsidies, pensions, and other government benefits.
     */
    GOVERNMENT_BENEFIT,

    /**
     * Educational grants and scholarships.
     */
    SCHOLARSHIP,

    /**
     * Insurance compensation and payouts.
     */
    INSURANCE_PAYOUT,

    /**
     * Borrowed money received as a loan.
     */
    LOAN_RECEIVED,

    /**
     * Fallback category for other income types.
     */
    OTHER
}

package bflow.common.financial;

import bflow.category.DTO.CategoryResponse;
import bflow.category.DTO.CategoryRequest;
import bflow.category.entity.Category;
import bflow.category.mapper.CategoryMapper;
import bflow.expenses.DTO.ExpenseResponse;
import bflow.expenses.entity.Expense;
import bflow.expenses.mapper.ExpenseMapper;
import bflow.income.DTO.IncomeResponse;
import bflow.income.entity.Income;
import bflow.income.mapper.IncomeMapper;
import bflow.tranfers.DTO.TransferenceResponse;
import bflow.tranfers.entities.Transfer;
import bflow.tranfers.mapper.TransferMapper;
import bflow.wallet.DTO.WalletResponse;
import bflow.wallet.entities.Wallet;
import bflow.wallet.entities.WalletUser;
import bflow.wallet.mapper.WalletMapper;
import org.mapstruct.factory.Mappers;

import java.util.List;

/**
 * Utility class for mapping financial transaction entities to DTOs.
 * Provides reusable mapping logic for category and transaction conversions.
 */
public final class TransactionMapper {

    /** Generated MapStruct mapper retained behind the legacy static API. */
    private static final CategoryMapper CATEGORY_MAPPER =
            Mappers.getMapper(CategoryMapper.class);

    /** Generated expense response mapper. */
    private static final ExpenseMapper EXPENSE_MAPPER =
            Mappers.getMapper(ExpenseMapper.class);

    /** Generated income response mapper. */
    private static final IncomeMapper INCOME_MAPPER =
            Mappers.getMapper(IncomeMapper.class);

    /** Generated transfer response mapper. */
    private static final TransferMapper TRANSFER_MAPPER =
            Mappers.getMapper(TransferMapper.class);

    /** Generated wallet response mapper. */
    private static final WalletMapper WALLET_MAPPER =
            Mappers.getMapper(WalletMapper.class);

    /**
     * Private constructor to prevent instantiation.
     * This is a utility class with static methods only.
     */
    private TransactionMapper() {
        throw new UnsupportedOperationException(
            "TransactionMapper is a utility class and cannot be instantiated"
        );
    }

    /**
     * Maps a Category entity to a CategoryResponse DTO.
     * Handles null values gracefully.
     *
     * @param category the category entity to map (can be null)
     * @return the mapped CategoryResponse or null if input is null
     */
    public static CategoryResponse mapCategoryToResponse(
            final Category category
    ) {
        return CATEGORY_MAPPER.toResponse(category);
    }

    /** Maps category request fields into a new entity. */
    public static Category mapCategoryRequestToEntity(
            final CategoryRequest request
    ) {
        return CATEGORY_MAPPER.toEntity(request);
    }

    /** Maps a category list using the generated element mapping. */
    public static List<CategoryResponse> mapCategoriesToResponses(
            final List<Category> categories
    ) {
        return CATEGORY_MAPPER.toResponses(categories);
    }

    /** Maps an expense entity to its API response. */
    public static ExpenseResponse mapExpenseToResponse(
            final Expense expense
    ) {
        return EXPENSE_MAPPER.toResponse(expense);
    }

    /** Maps an expense for the reduced wallet summary response. */
    public static ExpenseResponse mapExpenseToWalletSummary(
            final Expense expense
    ) {
        return EXPENSE_MAPPER.toWalletSummary(expense);
    }

    /** Maps an expense for the quick-expense response. */
    public static ExpenseResponse mapQuickExpenseToResponse(
            final Expense expense
    ) {
        return EXPENSE_MAPPER.toQuickResponse(expense);
    }

    /** Applies request-owned expense fields to an existing entity. */
    public static void updateExpenseFromRequest(
            final bflow.expenses.DTO.ExpenseRequest request,
            final Expense expense
    ) {
        EXPENSE_MAPPER.updateFromRequest(request, expense);
    }

    /** Maps an income entity to its API response. */
    public static IncomeResponse mapIncomeToResponse(final Income income) {
        return INCOME_MAPPER.toResponse(income);
    }

    /** Maps an income for the reduced wallet summary response. */
    public static IncomeResponse mapIncomeToWalletSummary(
            final Income income
    ) {
        return INCOME_MAPPER.toWalletSummary(income);
    }

    /** Applies request-owned income fields to an existing entity. */
    public static void updateIncomeFromRequest(
            final bflow.income.DTO.IncomeRequest request,
            final Income income
    ) {
        INCOME_MAPPER.updateFromRequest(request, income);
    }

    /** Maps a transfer entity to its API response. */
    public static TransferenceResponse mapTransferToResponse(
            final Transfer transfer
    ) {
        return TRANSFER_MAPPER.toResponse(transfer);
    }

    /** Maps wallet data and membership metadata to its API response. */
    public static WalletResponse mapWalletToResponse(
            final Wallet wallet,
            final WalletUser walletUser,
            final Integer memberCount
    ) {
        return WALLET_MAPPER.toResponse(wallet, walletUser, memberCount);
    }
}

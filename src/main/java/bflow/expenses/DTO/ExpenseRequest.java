package bflow.expenses.DTO;

import bflow.common.financial.BaseTransactionRequest;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

import java.util.UUID;

/**
 * DTO for creating or updating expense entries.
 */
@Getter
@Setter
public class ExpenseRequest extends BaseTransactionRequest {

    /** Indicates whether the expense is tax deductible. */
    @NotNull(message = "{expense.taxDeductible.required}")
    private Boolean taxDeductible = false;

    /** Indicates whether the expense is reimbursable. */
    @NotNull(message = "{expense.reimbursable.required}")
    private Boolean reimbursable = false;

    /**
     * Optional id of a previously uploaded {@code StoredFile} (in
     * {@code UPLOADED} status) to attach as this expense's receipt.
     */
    private UUID receiptFileId;

}

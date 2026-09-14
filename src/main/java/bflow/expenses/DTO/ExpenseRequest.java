package bflow.expenses.DTO;

import bflow.common.financial.BaseTransactionRequest;
import lombok.Getter;
import lombok.Setter;

import java.util.UUID;

/**
 * DTO for creating or updating expense entries.
 */
@Getter
@Setter
public class ExpenseRequest extends BaseTransactionRequest {

    /**
     * Optional id of a previously uploaded {@code StoredFile} (in
     * {@code UPLOADED} status) to attach as this expense's receipt.
     */
    private UUID receiptFileId;

}

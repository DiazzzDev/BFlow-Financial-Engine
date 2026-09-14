package bflow.income.DTO;

import bflow.common.financial.BaseTransactionRequest;
import lombok.Getter;
import lombok.Setter;

import java.util.UUID;

/**
 * Data transfer object for creating or updating income entries.
 */
@Getter
@Setter
public class IncomeRequest extends BaseTransactionRequest {
    /**
     * Optional id of a previously uploaded {@code StoredFile} (in
     * {@code UPLOADED} status) to attach as this expense's receipt.
     */
    private UUID receiptFileId;
}

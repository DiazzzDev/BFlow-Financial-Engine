package bflow.transactionimport.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.Setter;

/**
 * Result of processing a single row from an imported file.
 */
@Getter
@Setter
public class ImportRowResult {

    /** 1-based row number as it appears in the file (header excluded). */
    private int row;

    /** Whether this row was imported successfully. */
    private boolean success;

    /** Transaction type detected/declared for this row, if known. */
    @Schema(description = "Resolved transaction type for the imported row.",
            allowableValues = {"EXPENSE", "INCOME"})
    private String type;

    /** Id of the created expense/income, if successful. */
    private String transactionId;

    /** Error message, if the row failed. */
    private String message;

    /**
     * Builds a successful row result.
     *
     * @param row 1-based row number
     * @param type resolved transaction type
     * @param transactionId id of the created expense/income
     * @return the successful result
     */
    public static ImportRowResult ok(
            final int row, final String type, final String transactionId
    ) {
        ImportRowResult result = new ImportRowResult();
        result.setRow(row);
        result.setSuccess(true);
        result.setType(type);
        result.setTransactionId(transactionId);
        return result;
    }

    /**
     * Builds a failed row result.
     *
     * @param row 1-based row number
     * @param type transaction type, if it was known before the failure
     * @param message reason for the failure
     * @return the failed result
     */
    public static ImportRowResult failed(
            final int row, final String type, final String message
    ) {
        ImportRowResult result = new ImportRowResult();
        result.setRow(row);
        result.setSuccess(false);
        result.setType(type);
        result.setMessage(message);
        return result;
    }
}

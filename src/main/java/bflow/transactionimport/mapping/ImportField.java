package bflow.transactionimport.mapping;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Logical fields any transaction import (CSV, XLSX, etc.) needs,
 * independent of how the source file actually names its columns.
 */
@Schema(description = "Logical transaction field recognized by the import "
        + "column-mapping API.", allowableValues = {"TITLE", "DESCRIPTION",
        "AMOUNT", "DATE", "CATEGORY", "TYPE"})
public enum ImportField {
    /** Transaction title/concept. Required. */
    TITLE,

    /** Optional free-text description. */
    DESCRIPTION,

    /** Transaction amount. Required. */
    AMOUNT,

    /** Transaction date. Optional — defaults to today if absent. */
    DATE,

    /** Category name, used to resolve the category and infer type. Required. */
    CATEGORY,

    /** Optional explicit type (EXPENSE/INCOME), cross-checked against category. */
    TYPE
}

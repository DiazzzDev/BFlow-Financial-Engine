package bflow.common.financial;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

/** Shared title and description validation for financial transaction input. */
@Getter
@Setter
public abstract class BaseTransactionDetailsRequest {

    private static final int TITLE_MIN_LENGTH = 1;
    private static final int TITLE_MAX_LENGTH = 50;
    private static final int DESCRIPTION_MAX_LENGTH = 100;

    /** Transaction title. */
    @Schema(description = "Transaction title.", example = "Coffee")
    @NotBlank(message = "{transaction.title.required}")
    @Size(
            min = TITLE_MIN_LENGTH, max = TITLE_MAX_LENGTH,
            message = "{transaction.title.size}"
    )
    @Pattern(
            regexp = "^[\\p{L}0-9 .,'\\-()!?&@#%/+:_]+$",
            message = "{transaction.title.invalidCharacters}"
    )
    private String title;

    /** Optional transaction description. */
    @Schema(description = "Optional note for the transaction.",
            nullable = true, example = "Additional details")
    @Size(
            max = DESCRIPTION_MAX_LENGTH,
            message = "{transaction.description.size}"
    )
    @Pattern(
            regexp = "^[\\p{L}0-9 .,'\\-()!?&@#%/+:_]*$",
            message = "{transaction.description.invalidCharacters}"
    )
    private String description;
}

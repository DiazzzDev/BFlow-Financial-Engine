package bflow.wallet.DTO;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Request object for updating a Wallet.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class UpdateWalletRequest {
    /** Minimum wallet name length. */
    private static final int NAME_MIN_LENGTH = 2;

    /** Maximum wallet name length. */
    private static final int NAME_MAX_LENGTH = 100;

    /** Minimum description length. */
    private static final int DESCRIPTION_MIN_LENGTH = 3;

    /** Maximum description length. */
    private static final int DESCRIPTION_MAX_LENGTH = 255;

    /** The display name of the wallet. */
    @NotBlank(message = "{wallet.name.required}")
    @Size(
            min = NAME_MIN_LENGTH,
            max = NAME_MAX_LENGTH,
            message = "{wallet.name.size}"
    )
    @Pattern(
            regexp = "^[\\p{L}0-9 .,'\\-()]+$",
            message = "{wallet.name.invalidCharacters}"
    )
    private String name;

    /** The description of the wallet. */
    @NotBlank(message = "{wallet.description.required}")
    @Size(
            min = DESCRIPTION_MIN_LENGTH,
            max = DESCRIPTION_MAX_LENGTH,
            message = "{wallet.description.size}")
    @Pattern(
            regexp = "^[\\p{L}0-9 .,'\\-()]+$",
            message = "{wallet.description.invalidCharacters}"
    )
    private String description;
}

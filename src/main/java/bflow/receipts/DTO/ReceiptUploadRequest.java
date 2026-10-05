package bflow.receipts.DTO;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

import java.util.UUID;

/** Registers a previously uploaded file as a receipt. */
@Getter
@Setter
public class ReceiptUploadRequest {

    /** Identifier of the previously uploaded file. */
    @NotNull(message = "The file id is required")
    private UUID fileId;

    @Schema(description = "Optional wallet UUID. When omitted, the server "
            + "uses the caller's default OWNER wallet.", nullable = true)
    private UUID walletId;
}

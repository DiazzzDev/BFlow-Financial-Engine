package bflow.storage.enums;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Lifecycle status of a {@link bflow.storage.entity.StoredFile}.
 */
@Schema(description = "Stored file lifecycle: PENDING awaits upload "
        + "verification, UPLOADED is ready for use, and FAILED could not "
        + "be completed.", allowableValues = {"PENDING", "UPLOADED", "FAILED"})
public enum FileStatus {

    /**
     * A presigned upload was issued but the client has not yet
     * confirmed (or the object has not yet been verified in S3).
     */
    PENDING,

    /**
     * The upload was confirmed and the object exists in S3.
     */
    UPLOADED,

    /**
     * The upload never completed (e.g. the presigned URL expired
     * or the object could not be verified in S3).
     */
    FAILED
}

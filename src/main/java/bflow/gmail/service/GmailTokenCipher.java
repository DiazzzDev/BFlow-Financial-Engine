package bflow.gmail.service;

import org.springframework.stereotype.Component;
import software.amazon.awssdk.core.SdkBytes;
import software.amazon.awssdk.services.kms.KmsClient;
import software.amazon.awssdk.services.kms.model.DecryptRequest;
import software.amazon.awssdk.services.kms.model.EncryptRequest;

/** Small adapter around AWS KMS; encryption details and key rotation stay in AWS. */
@Component
public class GmailTokenCipher {
    private static final String KMS_KEY = "alias/bflow-gmail-refresh-tokens";
    private final KmsClient kmsClient;

    public GmailTokenCipher(final KmsClient kmsClient) {
        this.kmsClient = kmsClient;
    }

    public String encrypt(final String plaintext) {
        try {
            return java.util.Base64.getEncoder().encodeToString(kmsClient.encrypt(EncryptRequest.builder()
                    .keyId(KMS_KEY)
                    .plaintext(SdkBytes.fromUtf8String(plaintext))
                    .encryptionContext(java.util.Map.of("purpose", "bflow-gmail-refresh-token"))
                    .build()).ciphertextBlob().asByteArray());
        } catch (RuntimeException failure) {
            throw new IllegalStateException("Unable to protect Gmail credentials", failure);
        }
    }

    public String decrypt(final String encoded) {
        try {
            byte[] payload = java.util.Base64.getDecoder().decode(encoded);
            try {
                return kmsClient.decrypt(DecryptRequest.builder()
                        .ciphertextBlob(SdkBytes.fromByteArray(payload))
                        .encryptionContext(java.util.Map.of("purpose", "bflow-gmail-refresh-token"))
                        .build()).plaintext().asUtf8String();
            } finally {
                java.util.Arrays.fill(payload, (byte) 0);
            }
        } catch (RuntimeException failure) {
            throw new IllegalStateException("Unable to read protected Gmail credentials", failure);
        }
    }
}

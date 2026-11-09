package Diaz.Dev.BFlow.gmail;

import bflow.gmail.service.GmailTokenCipher;
import org.junit.jupiter.api.Test;
import software.amazon.awssdk.core.SdkBytes;
import software.amazon.awssdk.services.kms.KmsClient;
import software.amazon.awssdk.services.kms.model.DecryptResponse;
import software.amazon.awssdk.services.kms.model.EncryptResponse;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class GmailTokenCipherTest {
    @Test
    void protectsAndReadsRefreshTokensWithKms() {
        KmsClient kms = mock(KmsClient.class);
        when(kms.encrypt(any(software.amazon.awssdk.services.kms.model.EncryptRequest.class))).thenReturn(EncryptResponse.builder()
                .ciphertextBlob(SdkBytes.fromUtf8String("ciphertext")).build());
        when(kms.decrypt(any(software.amazon.awssdk.services.kms.model.DecryptRequest.class))).thenReturn(DecryptResponse.builder()
                .plaintext(SdkBytes.fromUtf8String("refresh-secret")).build());
        GmailTokenCipher cipher = new GmailTokenCipher(kms);

        String encrypted = cipher.encrypt("refresh-secret");

        assertThat(encrypted).isNotEqualTo("refresh-secret");
        assertThat(cipher.decrypt(encrypted)).isEqualTo("refresh-secret");
        verify(kms).encrypt(any(software.amazon.awssdk.services.kms.model.EncryptRequest.class));
        verify(kms).decrypt(any(software.amazon.awssdk.services.kms.model.DecryptRequest.class));
    }
}

package bflow.common.aws.config;

import com.google.auth.oauth2.GoogleCredentials;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import com.google.firebase.messaging.FirebaseMessaging;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.Base64;

/**
 * Configures the Firebase Admin SDK when FCM is explicitly enabled.
 * Credentials are supplied as base64-encoded JSON so the service account
 * key never needs to be committed to the repository or baked into an image.
 */
@Configuration
@ConditionalOnProperty(name = "firebase.enabled", havingValue = "true")
public class FirebaseMessagingConfig {

    /**
     * Creates the Firebase application from the injected service account.
     *
     * @param environment application configuration
     * @return initialized Firebase application
     * @throws IOException when the credentials cannot be decoded
     */
    @Bean
    public FirebaseApp firebaseApp(final Environment environment)
            throws IOException {
        String encodedCredentials = environment.getProperty(
                "firebase.service-account-json-base64", ""
        );
        if (encodedCredentials.isBlank()) {
            throw new IllegalStateException(
                    "Firebase is enabled but service-account credentials "
                            + "are missing"
            );
        }

        byte[] credentials = Base64.getDecoder().decode(encodedCredentials);
        GoogleCredentials googleCredentials = GoogleCredentials
                .fromStream(new ByteArrayInputStream(credentials));

        FirebaseOptions.Builder options = FirebaseOptions.builder()
                .setCredentials(googleCredentials);

        String projectId = environment.getProperty(
                "firebase.project-id", ""
        );
        if (!projectId.isBlank()) {
            options.setProjectId(projectId);
        }

        return FirebaseApp.initializeApp(options.build());
    }

    /**
     * Exposes the Firebase Messaging client for push delivery.
     *
     * @param firebaseApp initialized Firebase application
     * @return Firebase Messaging client
     */
    @Bean
    public FirebaseMessaging firebaseMessaging(
            final FirebaseApp firebaseApp
    ) {
        return FirebaseMessaging.getInstance(firebaseApp);
    }
}

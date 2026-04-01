package bflow.auth.security.jwt;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ClassPathResource;

import java.io.FileInputStream;
import java.io.InputStream;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;

/**
 * Configuration class for loading JWT RSA keys.
 */
@Configuration
public class JwtKeyConfig {

    /**
     * Loads the RSA private key from the classpath.
     * @param loader the key loader service.
     * @return the RSAPrivateKey bean.
     * @throws Exception if the key cannot be loaded.
     */
    @Bean
    RSAPrivateKey rsaPrivateKey(final RSAKeyLoader loader) throws Exception {
        String path = System.getenv("PRIVATE_KEY_PATH");

        InputStream is = (path != null)
                ? new FileInputStream(path)
                : new ClassPathResource("keys/private.pem").getInputStream();

        return loader.loadPrivateKey(is);
    }

    /**
     * Loads the RSA public key from the classpath.
     * @param loader the key loader service.
     * @return the RSAPublicKey bean.
     * @throws Exception if the key cannot be loaded.
     */
    @Bean
    RSAPublicKey rsaPublicKey(final RSAKeyLoader loader) throws Exception {
        String path = System.getenv("PUBLIC_KEY_PATH");

        InputStream is = (path != null)
                ? new FileInputStream(path)
                : new ClassPathResource("keys/public.pem").getInputStream();

        return loader.loadPublicKey(is);
    }
}

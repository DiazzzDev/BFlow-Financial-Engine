package bflow.auth.security;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.stereotype.Component;

/**
 * Validates Cognito ID tokens using the configured JWT decoder.
 */
@Component
@RequiredArgsConstructor
public class CognitoIdTokenValidator {

    /**
     * Decoder used to validate Cognito ID tokens.
     */
    @Qualifier("cognitoIdTokenDecoder")
    private final JwtDecoder idTokenDecoder;

    /**
     * Validates the provided Cognito ID token.
     *
     * @param idToken token to validate
     * @return decoded JWT
     */
    public Jwt validate(final String idToken) {
        try {
            return idTokenDecoder.decode(idToken);
        } catch (JwtException e) {
            throw new IllegalArgumentException(
                    "Invalid or expired id_token", e
            );
        }
    }
}

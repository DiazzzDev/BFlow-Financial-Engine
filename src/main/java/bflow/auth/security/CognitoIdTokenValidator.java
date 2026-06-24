package bflow.auth.security;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class CognitoIdTokenValidator {

    @Qualifier("cognitoIdTokenDecoder")
    private final JwtDecoder idTokenDecoder;

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
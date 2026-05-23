package bflow.auth.security.jwt;


import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jose.crypto.RSASSAVerifier;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Service;
import java.security.interfaces.RSAPublicKey;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.UUID;

/**
 * Implementation of JwtService using RSA (RS256).
 */
@Service
@RequiredArgsConstructor
public final class JwtServiceImpl implements JwtService {

    /** Key provider for RSA keys. */
    private final RsaKeyProvider rsaKeyProvider;

    /** 15 minutes. */
    private static final long ACCESS_TOKEN_TTL_SECONDS = 900;
    /** Cookie duration in days. */
    private static final int MAX_COOKIE_DAYS = 14;

    /** JWT issuer identifier. */
    private static final String ISSUER = "bflow-api";

    /** Expected JWT audience. */
    private static final String AUDIENCE = "bflow-client";

    @Override
    public String generateToken(final UUID userId, final String email,
                                final List<String> roles) {
        try {
            RsaKeyPair keys = rsaKeyProvider.getActive();

            JWTClaimsSet claims = new JWTClaimsSet.Builder()
                    .subject(userId.toString())
                    .claim("roles", roles)
                    .claim("email", email)
                    .jwtID(UUID.randomUUID().toString())
                    .issueTime(Date.from(Instant.now()))
                    .expirationTime(Date.from(
                            Instant.now().plusSeconds(ACCESS_TOKEN_TTL_SECONDS)
                    ))
                    .issuer("bflow-api")
                    .audience(AUDIENCE)
                    .build();

            JWSHeader header = new JWSHeader.Builder(JWSAlgorithm.RS256)
                    .keyID(keys.kid())
                    .build();

            SignedJWT jwt = new SignedJWT(header, claims);
            jwt.sign(new RSASSASigner(keys.privateKey()));

            return jwt.serialize();
        } catch (Exception e) {
            throw new IllegalStateException(
                    "Error while generating JWT Token", e
            );
        }
    }

    @Override
    public boolean validateToken(final String token) {
        try {
            SignedJWT jwt = SignedJWT.parse(token);

            String kid = jwt.getHeader().getKeyID();

            if (kid == null || kid.isBlank()) {
                return false;
            }

            RSAPublicKey key = rsaKeyProvider.getPublicKey(kid);

            JWTClaimsSet claims = jwt.getJWTClaimsSet();

            return jwt.verify(new RSASSAVerifier(key))
                    && claims.getExpirationTime().after(new Date())
                    && ISSUER.equals(claims.getIssuer())
                    && claims.getAudience().contains(AUDIENCE);
        } catch (Exception e) {
            return false;
        }
    }

    @Override
    public UUID extractUserId(final String token) {
        return UUID.fromString(getClaims(token).getSubject());
    }

    @Override
    public String extractEmail(final String token) {
        return (String) getClaims(token).getClaim("email");
    }

    @Override
    public List<String> extractRoles(final String token) {
        return (List<String>) getClaims(token).getClaim("roles");
    }

    private JWTClaimsSet getClaims(final String token) {
        try {
            return SignedJWT.parse(token).getJWTClaimsSet();
        } catch (Exception e) {
            throw new IllegalStateException("Invalid JWT", e);
        }
    }

    @Override
    public void attachAuthCookies(
            final HttpServletResponse response,
            final String accessToken,
            final String refreshToken
    ) {
        ResponseCookie accessCookie =
                ResponseCookie.from("access_token", accessToken)
                .httpOnly(true)
                .secure(true)
                .path("/")
                .sameSite("None")
                .maxAge(Duration.ofHours(1))
                .build();

        ResponseCookie refreshCookie =
                ResponseCookie.from("refresh_token", refreshToken)
                .httpOnly(true)
                .secure(true)
                .path("/")
                .sameSite("None")
                .maxAge(Duration.ofDays(MAX_COOKIE_DAYS))
                .build();

        response.addHeader(HttpHeaders.SET_COOKIE, accessCookie.toString());
        response.addHeader(HttpHeaders.SET_COOKIE, refreshCookie.toString());
    }

    @Override
    public void clearAuthCookies(final HttpServletResponse response) {

        ResponseCookie accessCookie =
                ResponseCookie.from("access_token", "")
                        .httpOnly(true)
                        .secure(true)
                        .path("/")
                        .sameSite("None")
                        .maxAge(0)
                        .build();

        ResponseCookie refreshCookie =
                ResponseCookie.from("refresh_token", "")
                        .httpOnly(true)
                        .secure(true)
                        .path("/")
                        .sameSite("None")
                        .maxAge(0)
                        .build();

        response.addHeader(HttpHeaders.SET_COOKIE, accessCookie.toString());
        response.addHeader(HttpHeaders.SET_COOKIE, refreshCookie.toString());
    }
}

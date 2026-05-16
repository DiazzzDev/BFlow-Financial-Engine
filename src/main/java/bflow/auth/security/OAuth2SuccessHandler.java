package bflow.auth.security;

import bflow.auth.entities.User;
import bflow.auth.enums.AuthProvider;
import bflow.auth.security.jwt.JwtService;
import bflow.auth.services.ServiceRefreshToken;
import bflow.auth.services.UserService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.stereotype.Component;

/**
 * Strategy used to handle successful OAuth2 authentication.
 */
@Component
@RequiredArgsConstructor
public final class OAuth2SuccessHandler
        implements AuthenticationSuccessHandler {

    /** Service for JWT operations. */
    private final JwtService jwtService;
    /** Service for User persistence logic. */
    private final UserService userService;

    /** Service for refresh token operations. */
    private final ServiceRefreshToken serviceRefreshToken;

    /** The frontend URL for redirecting on successful authentication. */
    //@Value("${app.frontend-url}")
    private final String frontendUrl = "http://localhost:5173/app/dashboard";

    @Override
    public void onAuthenticationSuccess(
            final HttpServletRequest request,
            final HttpServletResponse response,
            final Authentication authentication
    ) throws IOException {
        OAuth2User oAuth2User = (OAuth2User) authentication.getPrincipal();

        String email = oAuth2User.getAttribute("email");
        String providerId = oAuth2User.getAttribute("sub");
        Boolean emailVerified =
                oAuth2User.getAttribute("email_verified");

        if (Boolean.FALSE.equals(emailVerified)) {
            throw new IllegalStateException(
                    "OAuth2 email not verified"
            );
        }

        if (email == null || providerId == null) {
            throw new IllegalStateException(
                    "Google OAuth user missing required attributes"
            );
        }

        User user = userService.resolveOAuth2User(
                email,
                providerId,
                AuthProvider.GOOGLE
        );

        List<String> roles = List.copyOf(user.getRoles());

        String accessToken = jwtService.generateToken(
                user.getId(),
                user.getEmail(),
                roles
        );

        String refreshToken = UUID.randomUUID().toString();

        serviceRefreshToken.create(user.getId(), refreshToken);

        jwtService.attachAuthCookies(response, accessToken, refreshToken);
        response.sendRedirect(frontendUrl);
    }
}

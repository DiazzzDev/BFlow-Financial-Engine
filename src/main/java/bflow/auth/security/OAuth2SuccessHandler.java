package bflow.auth.security;

import bflow.auth.entities.User;
import bflow.auth.enums.AuthProvider;
import bflow.auth.security.jwt.JwtService;
import bflow.auth.services.UserService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
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

    @Value("${app.frontend-url}")
    private String frontendUrl;

    @Override
    public void onAuthenticationSuccess(
            final HttpServletRequest request,
            final HttpServletResponse response,
            final Authentication authentication
    ) throws IOException {
        OAuth2User oAuth2User = (OAuth2User) authentication.getPrincipal();

        String email = oAuth2User.getAttribute("email");
        String providerId = oAuth2User.getAttribute("sub");

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

        List<String> roles = user.getRoles()
                .stream()
                .map(r -> "ROLE_" + r)
                .toList();

        String accessToken = jwtService.generateToken(
                user.getId(),
                user.getEmail(),
                roles
        );

        String refreshToken = UUID.randomUUID().toString();

        jwtService.attachAuthCookies(response, accessToken, refreshToken);
        response.sendRedirect(frontendUrl);
    }
}

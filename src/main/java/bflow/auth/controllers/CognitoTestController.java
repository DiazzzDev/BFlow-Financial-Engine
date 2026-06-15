package bflow.auth.controllers;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class CognitoTestController {

    @GetMapping("/api/test")
    public Object test(
            @AuthenticationPrincipal Jwt jwt
    ) {
        return jwt.getClaims();
    }

}
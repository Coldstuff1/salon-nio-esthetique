package ca.nio.backend.security;

import java.util.List;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/me")
public class MeController {

    @GetMapping
    public Me me(@AuthenticationPrincipal Jwt jwt) {
        return new Me(
                jwt.getClaimAsString("preferred_username"),
                jwt.getClaimAsString("email"),
                KeycloakJwtAuthenticationConverter.realmRoles(jwt));
    }

    public record Me(String username, String email, List<String> roles) {
    }

}

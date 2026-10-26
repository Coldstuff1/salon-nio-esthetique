package ca.nio.backend.security;

import java.util.List;

import org.jspecify.annotations.Nullable;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import ca.nio.backend.services.CurrentUserService;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/me")
@RequiredArgsConstructor
public class MeController {

    private final CurrentUserService currentUser;

    @GetMapping
    public Me me(@AuthenticationPrincipal Jwt jwt) {
        List<String> roles = CurrentUserService.realmRoles(jwt);
        currentUser.syncRecipient(jwt);
        if (roles.contains("client")) {
            currentUser.customer(jwt);
        }
        return new Me(
                jwt.getClaimAsString("preferred_username"),
                jwt.getClaimAsString("email"),
                roles);
    }

    public record Me(String username, @Nullable String email, List<String> roles) {
    }

}

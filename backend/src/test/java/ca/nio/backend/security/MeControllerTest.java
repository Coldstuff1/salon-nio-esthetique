package ca.nio.backend.security;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.JwtRequestPostProcessor;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@WebMvcTest(MeController.class)
@Import({SecurityConfig.class, MeControllerTest.AdminOnlyController.class})
class MeControllerTest {

    @Autowired
    MockMvc mockMvc;

    @Test
    void rejectsRequestWithoutToken() throws Exception {
        mockMvc.perform(get("/api/me"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void returnsUserInfoFromToken() throws Exception {
        mockMvc.perform(get("/api/me").with(keycloakJwt("admin1", "admin")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value("admin1"))
                .andExpect(jsonPath("$.email").value("admin1@example.com"))
                .andExpect(jsonPath("$.roles[0]").value("admin"));
    }

    @Test
    void adminEndpointAllowsAdminRole() throws Exception {
        mockMvc.perform(get("/api/test/admin").with(keycloakJwt("admin1", "admin")))
                .andExpect(status().isOk());
    }

    @Test
    void adminEndpointRejectsClientRole() throws Exception {
        mockMvc.perform(get("/api/test/admin").with(keycloakJwt("client1", "client")))
                .andExpect(status().isForbidden());
    }

    private static JwtRequestPostProcessor keycloakJwt(String username, String... roles) {
        return jwt()
                .jwt(token -> token
                        .claim("preferred_username", username)
                        .claim("email", username + "@example.com")
                        .claim("realm_access", Map.of("roles", List.of(roles))))
                .authorities(new KeycloakJwtAuthenticationConverter()::authorities);
    }

    @RestController
    static class AdminOnlyController {

        @GetMapping("/api/test/admin")
        @PreAuthorize("hasRole('ADMIN')")
        String admin() {
            return "ok";
        }

    }

}

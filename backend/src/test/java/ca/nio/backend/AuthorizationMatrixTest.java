package ca.nio.backend;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.request;

import java.util.Arrays;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Stream;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.JwtRequestPostProcessor;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import ca.nio.backend.security.KeycloakJwtAuthenticationConverter;

/**
 * Every endpoint against every kind of caller: the wrong caller must get 401 (anonymous) or 403, the right one
 * must get past security (the empty body then usually fails validation, which is fine here).
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
class AuthorizationMatrixTest {

    enum Caller { ANONYMOUS, CLIENT, EMPLOYEE, ADMIN }

    static final Set<Caller> EVERYONE = EnumSet.allOf(Caller.class);
    static final Set<Caller> SIGNED_IN = EnumSet.of(Caller.CLIENT, Caller.EMPLOYEE, Caller.ADMIN);
    static final Set<Caller> CLIENT = EnumSet.of(Caller.CLIENT);
    static final Set<Caller> EMPLOYEE = EnumSet.of(Caller.EMPLOYEE);
    static final Set<Caller> ADMIN = EnumSet.of(Caller.ADMIN);

    static final String ID = UUID.randomUUID().toString();

    record Endpoint(HttpMethod method, String path, Set<Caller> allowed) {

        @Override
        public String toString() {
            return method + " " + path.replace(ID, "{id}");
        }

    }

    @Autowired
    MockMvc mockMvc;

    static Stream<Arguments> matrix() {
        List<Endpoint> endpoints = List.of(
                new Endpoint(HttpMethod.GET, "/api/public/service-types", EVERYONE),
                new Endpoint(HttpMethod.GET, "/api/public/services", EVERYONE),
                new Endpoint(HttpMethod.GET, "/api/public/products", EVERYONE),
                new Endpoint(HttpMethod.GET, "/api/public/employees", EVERYONE),
                new Endpoint(HttpMethod.GET, "/api/public/employees/" + ID + "/offerings", EVERYONE),
                new Endpoint(HttpMethod.GET, "/api/public/employees/" + ID + "/slots", EVERYONE),
                new Endpoint(HttpMethod.POST, "/api/public/quote", EVERYONE),
                new Endpoint(HttpMethod.GET, "/api/public/push-key", EVERYONE),

                new Endpoint(HttpMethod.GET, "/api/me", SIGNED_IN),
                new Endpoint(HttpMethod.GET, "/api/me/profile", CLIENT),
                new Endpoint(HttpMethod.PUT, "/api/me/profile", CLIENT),
                new Endpoint(HttpMethod.GET, "/api/me/notifications", SIGNED_IN),
                new Endpoint(HttpMethod.GET, "/api/me/notifications/unread-count", SIGNED_IN),
                new Endpoint(HttpMethod.POST, "/api/me/notifications/" + ID + "/read", SIGNED_IN),
                new Endpoint(HttpMethod.POST, "/api/me/notifications/read-all", SIGNED_IN),
                new Endpoint(HttpMethod.PUT, "/api/me/push-subscriptions", SIGNED_IN),
                new Endpoint(HttpMethod.DELETE, "/api/me/push-subscriptions", SIGNED_IN),

                new Endpoint(HttpMethod.POST, "/api/bookings", CLIENT),
                new Endpoint(HttpMethod.GET, "/api/bookings/mine", CLIENT),
                new Endpoint(HttpMethod.POST, "/api/bookings/" + ID + "/cancel", SIGNED_IN),
                new Endpoint(HttpMethod.POST, "/api/bookings/" + ID + "/complete", EMPLOYEE),
                new Endpoint(HttpMethod.POST, "/api/bookings/" + ID + "/no-show", EMPLOYEE),

                new Endpoint(HttpMethod.GET, "/api/staff/me", EMPLOYEE),
                new Endpoint(HttpMethod.PUT, "/api/staff/me", EMPLOYEE),
                new Endpoint(HttpMethod.PUT, "/api/staff/me/photo", EMPLOYEE),
                new Endpoint(HttpMethod.DELETE, "/api/staff/me/photo", EMPLOYEE),
                new Endpoint(HttpMethod.PUT, "/api/staff/me/availability", EMPLOYEE),
                new Endpoint(HttpMethod.POST, "/api/staff/me/time-off", EMPLOYEE),
                new Endpoint(HttpMethod.DELETE, "/api/staff/me/time-off/" + ID, EMPLOYEE),
                new Endpoint(HttpMethod.PUT, "/api/staff/me/service-offerings/" + ID, EMPLOYEE),
                new Endpoint(HttpMethod.PUT, "/api/staff/me/product-offerings/" + ID, EMPLOYEE),
                new Endpoint(HttpMethod.GET, "/api/staff/me/schedule", EMPLOYEE),

                new Endpoint(HttpMethod.POST, "/api/media/uploads", SIGNED_IN),

                new Endpoint(HttpMethod.POST, "/api/bug-reports", SIGNED_IN),

                new Endpoint(HttpMethod.GET, "/api/admin/bookings", ADMIN),
                new Endpoint(HttpMethod.GET, "/api/admin/service-types", ADMIN),
                new Endpoint(HttpMethod.POST, "/api/admin/service-types", ADMIN),
                new Endpoint(HttpMethod.PUT, "/api/admin/service-types/" + ID, ADMIN),
                new Endpoint(HttpMethod.DELETE, "/api/admin/service-types/" + ID, ADMIN),
                new Endpoint(HttpMethod.GET, "/api/admin/products", ADMIN),
                new Endpoint(HttpMethod.POST, "/api/admin/products", ADMIN),
                new Endpoint(HttpMethod.PUT, "/api/admin/products/" + ID, ADMIN),
                new Endpoint(HttpMethod.DELETE, "/api/admin/products/" + ID, ADMIN),
                new Endpoint(HttpMethod.GET, "/api/admin/employees", ADMIN),
                new Endpoint(HttpMethod.POST, "/api/admin/employees", ADMIN),
                new Endpoint(HttpMethod.PUT, "/api/admin/employees/" + ID, ADMIN),
                new Endpoint(HttpMethod.DELETE, "/api/admin/employees/" + ID, ADMIN),
                new Endpoint(HttpMethod.GET, "/api/admin/bug-reports", ADMIN),
                new Endpoint(HttpMethod.GET, "/api/admin/bug-reports/" + ID, ADMIN),
                new Endpoint(HttpMethod.PUT, "/api/admin/bug-reports/" + ID, ADMIN),

                // anything unmapped stays closed to anonymous callers
                new Endpoint(HttpMethod.GET, "/api/unknown", SIGNED_IN));
        return endpoints.stream()
                .flatMap(endpoint -> Arrays.stream(Caller.values()).map(caller -> Arguments.of(endpoint, caller)));
    }

    @ParameterizedTest(name = "{0} as {1}")
    @MethodSource("matrix")
    void onlyAllowedCallersGetPastSecurity(Endpoint endpoint, Caller caller) throws Exception {
        MockHttpServletRequestBuilder request = request(endpoint.method(), endpoint.path())
                .contentType(MediaType.APPLICATION_JSON).content("{}");
        if (caller != Caller.ANONYMOUS) {
            request.with(token(caller));
        }

        int status = mockMvc.perform(request).andReturn().getResponse().getStatus();

        if (endpoint.allowed().contains(caller)) {
            assertThat(status).as(endpoint + " as " + caller).isNotIn(401, 403);
        } else {
            assertThat(status).as(endpoint + " as " + caller).isEqualTo(caller == Caller.ANONYMOUS ? 401 : 403);
        }
    }

    private static JwtRequestPostProcessor token(Caller caller) {
        UUID subject = UUID.randomUUID();
        String role = caller.name().toLowerCase(java.util.Locale.ROOT);
        return jwt()
                .jwt(token -> token
                        .subject(subject.toString())
                        .claim("preferred_username", role + "-" + subject)
                        .claim("email_verified", true)
                        .claim("realm_access", Map.of("roles", List.of(role))))
                .authorities(new KeycloakJwtAuthenticationConverter()::authorities);
    }

}

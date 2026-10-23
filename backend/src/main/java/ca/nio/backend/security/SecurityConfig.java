package ca.nio.backend.security;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
@EnableMethodSecurity
public class SecurityConfig {

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/api/public/**", "/v3/api-docs/**", "/swagger-ui/**", "/swagger-ui.html",
                                "/error", "/actuator/health/**").permitAll()
                        // the controllers check roles too; these keep a forgotten annotation from opening an area
                        .requestMatchers("/api/admin/**").hasRole("ADMIN")
                        .requestMatchers("/api/staff/**").hasRole("EMPLOYEE")
                        .requestMatchers("/api/media/**", "/api/bug-reports/**").hasAnyRole("CLIENT", "EMPLOYEE", "ADMIN")
                        .requestMatchers("/api/me/profile/**", "/api/bookings/mine").hasRole("CLIENT")
                        .requestMatchers(HttpMethod.POST, "/api/bookings").hasRole("CLIENT")
                        .requestMatchers("/api/bookings/*/complete", "/api/bookings/*/no-show").hasRole("EMPLOYEE")
                        .anyRequest().authenticated())
                .oauth2ResourceServer(oauth2 -> oauth2
                        .jwt(jwt -> jwt.jwtAuthenticationConverter(new KeycloakJwtAuthenticationConverter())));
        return http.build();
    }

}

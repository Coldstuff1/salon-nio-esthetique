package ca.nio.backend.controllers;

import java.lang.reflect.AnnotatedElement;
import java.lang.reflect.Field;
import java.lang.reflect.RecordComponent;
import java.util.Arrays;
import java.util.Iterator;
import java.util.Map;
import java.util.Optional;

import org.jspecify.annotations.Nullable;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import io.swagger.v3.core.converter.AnnotatedType;
import io.swagger.v3.core.converter.ModelConverter;
import io.swagger.v3.core.converter.ModelConverterContext;
import io.swagger.v3.core.util.Json;
import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.enums.SecuritySchemeType;
import io.swagger.v3.oas.annotations.info.Info;
import io.swagger.v3.oas.annotations.security.OAuthFlow;
import io.swagger.v3.oas.annotations.security.OAuthFlows;
import io.swagger.v3.oas.annotations.security.OAuthScope;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.security.SecurityScheme;
import io.swagger.v3.oas.models.media.Schema;

/**
 * OpenAPI spec served at {@code /v3/api-docs}; the frontend generates its typed client from it.
 */
@Configuration
@OpenAPIDefinition(info = @Info(title = "Nio Esthétique API", version = "v1"),
        security = {@SecurityRequirement(name = "keycloak"), @SecurityRequirement(name = "bearer-jwt")})
@SecurityScheme(name = "keycloak", type = SecuritySchemeType.OAUTH2, description = "Log in through Keycloak",
        flows = @OAuthFlows(authorizationCode = @OAuthFlow(
                authorizationUrl = "${spring.security.oauth2.resourceserver.jwt.issuer-uri}/protocol/openid-connect/auth",
                tokenUrl = "${spring.security.oauth2.resourceserver.jwt.issuer-uri}/protocol/openid-connect/token",
                scopes = @OAuthScope(name = "openid"))))
@SecurityScheme(name = "bearer-jwt", type = SecuritySchemeType.HTTP, scheme = "bearer", bearerFormat = "JWT",
        description = "Paste an access token")
public class OpenApiConfig {

    /**
     * For our own types, every property is required unless its field is {@link Nullable}, which becomes an
     * optional, nullable property. Generated TypeScript then only uses {@code ?} where a value can really be
     * missing.
     */
    @Bean
    ModelConverter requiredUnlessNullable() {
        return new ModelConverter() {
            @Override
            public Schema<?> resolve(AnnotatedType type, ModelConverterContext context,
                                     Iterator<ModelConverter> chain) {
                Schema<?> resolved = chain.hasNext() ? chain.next().resolve(type, context, chain) : null;
                Class<?> raw = Json.mapper().constructType(type.getType()).getRawClass();
                if (resolved == null || !raw.getPackageName().startsWith("ca.nio.backend")) {
                    return resolved;
                }
                // named models come back as a $ref; the properties live on the model defined in the context
                String ref = resolved.get$ref();
                Schema<?> model = ref == null ? resolved
                        : context.getDefinedModels().get(ref.substring(ref.lastIndexOf('/') + 1));
                if (model == null || model.getProperties() == null) {
                    return resolved;
                }
                for (Map.Entry<String, Schema> property : model.getProperties().entrySet()) {
                    String name = property.getKey();
                    if (member(raw, name).map(OpenApiConfig::isNullable).orElse(false)) {
                        if (model.getRequired() != null) {
                            model.getRequired().remove(name);
                        }
                        if (property.getValue().getTypes() != null) {
                            property.getValue().addType("null");
                        }
                    } else if (model.getRequired() == null || !model.getRequired().contains(name)) {
                        model.addRequiredItem(name);
                    }
                }
                return resolved;
            }
        };
    }

    private static Optional<AnnotatedElement> member(Class<?> type, String name) {
        if (type.isRecord()) {
            return Arrays.stream(type.getRecordComponents())
                    .filter(c -> c.getName().equals(name))
                    .<AnnotatedElement>map(RecordComponent::getAnnotatedType)
                    .findFirst();
        }
        for (Class<?> c = type; c != null && c != Object.class; c = c.getSuperclass()) {
            Optional<AnnotatedElement> field = Arrays.stream(c.getDeclaredFields())
                    .filter(f -> f.getName().equals(name))
                    .<AnnotatedElement>map(Field::getAnnotatedType)
                    .findFirst();
            if (field.isPresent()) {
                return field;
            }
        }
        return Optional.empty();
    }

    private static boolean isNullable(AnnotatedElement element) {
        return element.isAnnotationPresent(Nullable.class);
    }

}

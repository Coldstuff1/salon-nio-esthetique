package ca.nio.backend;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.SerializationFeature;
import tools.jackson.databind.json.JsonMapper;

/**
 * Exports the OpenAPI spec to {@code frontend/openapi.json}, which the frontend's {@code npm run gen:api} turns
 * into TypeScript types. Running the tests keeps the committed spec in sync with the controllers.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
class OpenApiSpecTest {

    static final Path FRONTEND = Path.of("..", "frontend");

    @Autowired
    MockMvc mockMvc;

    @Test
    void exportsSpecForFrontend() throws Exception {
        String body = mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        JsonMapper mapper = JsonMapper.builder().enable(SerializationFeature.INDENT_OUTPUT).build();
        JsonNode spec = mapper.readTree(body);
        assertThat(spec.at("/paths/~1api~1bookings/post").isMissingNode()).isFalse();
        assertThat(spec.at("/components/securitySchemes/bearer-jwt").isMissingNode()).isFalse();
        // error responses stay documented; the frontend types failed calls from them
        assertThat(spec.at("/components/schemas/ProblemDetail").isMissingNode()).isFalse();
        assertThat(spec.at("/paths/~1api~1bookings/post/responses/409").isMissingNode()).isFalse();
        assertThat(spec.at("/components/securitySchemes/keycloak/flows/authorizationCode/authorizationUrl").asString())
                .isEqualTo("http://localhost:8180/realms/nio/protocol/openid-connect/auth");

        if (Files.isDirectory(FRONTEND)) {
            Files.writeString(FRONTEND.resolve("openapi.json"), mapper.writeValueAsString(spec) + "\n");
        }
    }

}

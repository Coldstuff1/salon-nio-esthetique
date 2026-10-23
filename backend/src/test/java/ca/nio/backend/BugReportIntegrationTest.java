package ca.nio.backend;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.hamcrest.Matchers;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.JwtRequestPostProcessor;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import com.jayway.jsonpath.JsonPath;

import ca.nio.backend.security.KeycloakJwtAuthenticationConverter;

/**
 * Reporting a problem with a screenshot, then an admin finding and triaging it.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
class BugReportIntegrationTest {

    static final byte[] JPEG = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0, 1, 2, 3, 4};
    static final byte[] MP4 = {0, 0, 0, 0x18, 'f', 't', 'y', 'p', 'i', 's', 'o', 'm', 0, 0, 2, 0};

    @Autowired
    MockMvc mockMvc;

    HttpClient http = HttpClient.newHttpClient();
    UUID clientId = UUID.randomUUID();

    @Test
    void clientReportsAProblemWithAScreenshotAndAdminSeesIt() throws Exception {
        UUID screenshotId = upload(client(), "image/jpeg", JPEG);
        Map<String, Object> body = report("Le bouton ne marche pas");
        body.put("screenshotId", screenshotId);
        body.put("locale", "fr");
        body.put("viewport", "390x844");

        String id = JsonPath.read(send(post("/api/bug-reports").header(HttpHeaders.USER_AGENT, "Firefox/140"),
                client(), body)
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString(), "$.id");

        mockMvc.perform(get("/api/admin/bug-reports").with(admin()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.id == '" + id + "')].status").value("NEW"))
                .andExpect(jsonPath("$[?(@.id == '" + id + "')].reporterName").value("Marie Tremblay"))
                .andExpect(jsonPath("$[?(@.id == '" + id + "')].reporterRoles[*]").value("client"))
                .andExpect(jsonPath("$[?(@.id == '" + id + "')].hasScreenshot").value(true));

        String detail = mockMvc.perform(get("/api/admin/bug-reports/" + id).with(admin()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("Le bouton ne marche pas"))
                .andExpect(jsonPath("$.pagePath").value("/booking?step=2"))
                .andExpect(jsonPath("$.userAgent").value("Firefox/140"))
                .andExpect(jsonPath("$.reporterEmail").value("marie@example.com"))
                .andExpect(jsonPath("$.reporterRoles").value(Matchers.contains("client")))
                .andExpect(jsonPath("$.viewport").value("390x844"))
                .andReturn().getResponse().getContentAsString();
        HttpResponse<byte[]> download = http.send(
                HttpRequest.newBuilder(URI.create(JsonPath.read(detail, "$.screenshotUrl"))).build(),
                HttpResponse.BodyHandlers.ofByteArray());
        assertThat(download.statusCode()).isEqualTo(200);
        assertThat(download.body()).isEqualTo(JPEG);
    }

    @Test
    void rejectsAVideoScreenshot() throws Exception {
        JwtRequestPostProcessor employee = keycloakJwt(UUID.randomUUID(), "employee");
        UUID videoId = upload(employee, "video/mp4", MP4);
        Map<String, Object> body = report("Vidéo");
        body.put("screenshotId", videoId);

        send(post("/api/bug-reports"), employee, body)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("file-type"));
    }

    @Test
    void rejectsSomeoneElsesScreenshot() throws Exception {
        UUID theirs = upload(keycloakJwt(UUID.randomUUID(), "employee"), "image/jpeg", JPEG);
        Map<String, Object> body = report("Pas la mienne");
        body.put("screenshotId", theirs);

        send(post("/api/bug-reports"), client(), body)
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("media-not-found"));
    }

    @Test
    void capsReportsPerDay() throws Exception {
        for (int i = 0; i < 10; i++) {
            send(post("/api/bug-reports"), client(), report("Problème " + i)).andExpect(status().isCreated());
        }

        send(post("/api/bug-reports"), client(), report("Un de trop"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("too-many-bug-reports"))
                .andExpect(jsonPath("$.params.max").value(10));
    }

    @Test
    void validatesTheReport() throws Exception {
        Map<String, Object> body = report(" ");
        body.put("description", "x".repeat(5001));

        send(post("/api/bug-reports"), client(), body)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fields").value(Matchers.hasItems("title", "description")));
    }

    @Test
    void adminTriagesAReport() throws Exception {
        String id = JsonPath.read(send(post("/api/bug-reports"), client(), report("À trier"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString(), "$.id");

        send(put("/api/admin/bug-reports/" + id), admin(), Map.of("status", "IN_PROGRESS", "adminNote", " Je regarde "))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/admin/bug-reports/" + id).with(admin()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("IN_PROGRESS"))
                .andExpect(jsonPath("$.adminNote").value("Je regarde"))
                .andExpect(jsonPath("$.updatedAt").isNotEmpty());
    }

    @Test
    void unknownReportIsNotFound() throws Exception {
        mockMvc.perform(get("/api/admin/bug-reports/" + UUID.randomUUID()).with(admin()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("bug-report-not-found"));
    }

    private static Map<String, Object> report(String title) {
        Map<String, Object> body = new HashMap<>();
        body.put("title", title);
        body.put("description", "Quand je clique, rien ne se passe.");
        body.put("pagePath", "/booking?step=2");
        return body;
    }

    /** Does what the browser does: ask for a URL, then PUT the bytes straight to storage. */
    private UUID upload(JwtRequestPostProcessor auth, String contentType, byte[] bytes) throws Exception {
        String body = send(post("/api/media/uploads"), auth, Map.of("contentType", contentType, "sizeBytes", bytes.length))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        HttpResponse<String> response = http.send(HttpRequest.newBuilder(URI.create(JsonPath.read(body, "$.uploadUrl")))
                        .header("Content-Type", contentType)
                        .PUT(HttpRequest.BodyPublishers.ofByteArray(bytes)).build(),
                HttpResponse.BodyHandlers.ofString());
        assertThat(response.statusCode()).as(response.body()).isEqualTo(200);
        return UUID.fromString(JsonPath.read(body, "$.mediaId"));
    }

    private ResultActions send(MockHttpServletRequestBuilder request, JwtRequestPostProcessor auth, Object body)
            throws Exception {
        return mockMvc.perform(request.contentType(MediaType.APPLICATION_JSON).content(json(body)).with(auth));
    }

    private JwtRequestPostProcessor client() {
        return jwt()
                .jwt(token -> token
                        .subject(clientId.toString())
                        .claim("preferred_username", "marie")
                        .claim("name", "Marie Tremblay")
                        .claim("email", "marie@example.com")
                        .claim("realm_access", Map.of("roles", List.of("default-roles-nio", "client"))))
                .authorities(new KeycloakJwtAuthenticationConverter()::authorities);
    }

    private static JwtRequestPostProcessor admin() {
        return keycloakJwt(UUID.randomUUID(), "admin");
    }

    private static JwtRequestPostProcessor keycloakJwt(UUID subject, String role) {
        return jwt()
                .jwt(token -> token
                        .subject(subject.toString())
                        .claim("preferred_username", role + "-" + subject)
                        .claim("realm_access", Map.of("roles", List.of(role))))
                .authorities(new KeycloakJwtAuthenticationConverter()::authorities);
    }

    private static String json(Object body) {
        return tools.jackson.databind.json.JsonMapper.builder().build().writeValueAsString(body);
    }

}

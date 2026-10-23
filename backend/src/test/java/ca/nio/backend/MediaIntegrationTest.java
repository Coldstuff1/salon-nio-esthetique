package ca.nio.backend;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.hamcrest.Matchers;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.JwtRequestPostProcessor;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import com.jayway.jsonpath.JsonPath;

import ca.nio.backend.model.Media;
import ca.nio.backend.repositories.MediaRepository;
import ca.nio.backend.security.KeycloakJwtAuthenticationConverter;
import ca.nio.backend.services.MediaService;
import ca.nio.backend.services.MediaStorage;

/**
 * Presigned upload against a real S3-compatible store, then attaching the result as an employee photo.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
class MediaIntegrationTest {

    static final byte[] JPEG = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0, 1, 2, 3, 4};

    @Autowired
    MockMvc mockMvc;
    @Autowired
    MediaRepository media;
    @Autowired
    MediaStorage storage;
    @Autowired
    MediaService mediaService;

    HttpClient http = HttpClient.newHttpClient();
    UUID employeeId = UUID.randomUUID();

    @Test
    void employeeUploadsAndShowsAPhoto() throws Exception {
        UUID mediaId = upload(employee(), "image/jpeg", JPEG);

        send(put("/api/staff/me/photo"), employee(), Map.of("mediaId", mediaId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.photoUrl").isNotEmpty());

        List<String> photoUrls = JsonPath.read(mockMvc.perform(get("/api/public/employees"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(),
                "$[?(@.id == '" + employeeId + "')].photoUrl");
        String photoUrl = photoUrls.getFirst();
        HttpResponse<byte[]> download = http.send(HttpRequest.newBuilder(URI.create(photoUrl)).build(),
                HttpResponse.BodyHandlers.ofByteArray());
        assertThat(download.statusCode()).isEqualTo(200);
        assertThat(download.body()).isEqualTo(JPEG);
    }

    @Test
    void replacingThePhotoDeletesTheOldOne() throws Exception {
        UUID first = upload(employee(), "image/jpeg", JPEG);
        send(put("/api/staff/me/photo"), employee(), Map.of("mediaId", first)).andExpect(status().isOk());
        UUID second = upload(employee(), "image/jpeg", JPEG);
        send(put("/api/staff/me/photo"), employee(), Map.of("mediaId", second)).andExpect(status().isOk());

        assertThat(media.existsById(first)).isFalse();
        assertThat(storage.head(Media.objectKey(first))).isEmpty();
        assertThat(storage.head(Media.objectKey(second))).isPresent();
    }

    @Test
    void removingThePhotoClearsIt() throws Exception {
        UUID mediaId = upload(employee(), "image/jpeg", JPEG);
        send(put("/api/staff/me/photo"), employee(), Map.of("mediaId", mediaId)).andExpect(status().isOk());

        mockMvc.perform(delete("/api/staff/me/photo").with(employee()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.photoUrl").value(Matchers.nullValue()));
        assertThat(storage.head(Media.objectKey(mediaId))).isEmpty();
    }

    @Test
    void rejectsUnsupportedTypesAndOversizedFiles() throws Exception {
        requestUpload(employee(), "application/pdf", 100).andExpect(status().isBadRequest());
        requestUpload(employee(), "image/png", 11L * 1024 * 1024).andExpect(status().isBadRequest());
        requestUpload(employee(), "video/mp4", 11L * 1024 * 1024).andExpect(status().isCreated());
    }

    @Test
    void rejectsClaimingBeforeUploading() throws Exception {
        UUID mediaId = ticket(requestUpload(employee(), "image/jpeg", JPEG.length));

        send(put("/api/staff/me/photo"), employee(), Map.of("mediaId", mediaId))
                .andExpect(status().isBadRequest());
    }

    @Test
    void rejectsClaimingSomeoneElsesUpload() throws Exception {
        UUID theirs = upload(keycloakJwt(UUID.randomUUID(), "employee"), "image/jpeg", JPEG);

        send(put("/api/staff/me/photo"), employee(), Map.of("mediaId", theirs))
                .andExpect(status().isNotFound());
    }

    @Test
    void uploadUrlOnlyAcceptsTheDeclaredContentType() throws Exception {
        String body = requestUpload(employee(), "image/jpeg", JPEG.length)
                .andReturn().getResponse().getContentAsString();
        HttpResponse<Void> response = http.send(HttpRequest.newBuilder(URI.create(JsonPath.read(body, "$.uploadUrl")))
                        .header("Content-Type", "image/png")
                        .PUT(HttpRequest.BodyPublishers.ofByteArray(JPEG)).build(),
                HttpResponse.BodyHandlers.discarding());

        assertThat(response.statusCode()).isEqualTo(403);
    }

    @Test
    void purgesUploadsThatWereNeverClaimed() throws Exception {
        UUID mediaId = upload(employee(), "image/jpeg", JPEG);
        Media stale = media.findById(mediaId).orElseThrow();
        stale.setCreatedAt(Instant.now().minusSeconds(25 * 3600));
        media.save(stale);

        assertThat(mediaService.purgeStalePending()).isPositive();
        assertThat(media.existsById(mediaId)).isFalse();
        assertThat(storage.head(Media.objectKey(mediaId))).isEmpty();
    }

    @Test
    void anonymousUsersCannotUpload() throws Exception {
        send(post("/api/media/uploads"), null, Map.of("contentType", "image/jpeg", "sizeBytes", 10))
                .andExpect(status().isUnauthorized());
    }

    /** Does what the browser does: ask for a URL, then PUT the bytes straight to storage. */
    private UUID upload(JwtRequestPostProcessor auth, String contentType, byte[] bytes) throws Exception {
        String body = requestUpload(auth, contentType, bytes.length)
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        HttpResponse<String> response = http.send(HttpRequest.newBuilder(URI.create(JsonPath.read(body, "$.uploadUrl")))
                        .header("Content-Type", contentType)
                        .PUT(HttpRequest.BodyPublishers.ofByteArray(bytes)).build(),
                HttpResponse.BodyHandlers.ofString());
        assertThat(response.statusCode()).as(response.body()).isEqualTo(200);
        return UUID.fromString(JsonPath.read(body, "$.mediaId"));
    }

    private ResultActions requestUpload(JwtRequestPostProcessor auth, String contentType, long size) throws Exception {
        return send(post("/api/media/uploads"), auth, Map.of("contentType", contentType, "sizeBytes", size));
    }

    private static UUID ticket(ResultActions result) throws Exception {
        return UUID.fromString(JsonPath.read(result.andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString(), "$.mediaId"));
    }

    private ResultActions send(MockHttpServletRequestBuilder request, JwtRequestPostProcessor auth, Object body)
            throws Exception {
        request.contentType(MediaType.APPLICATION_JSON).content(json(body));
        return mockMvc.perform(auth == null ? request : request.with(auth));
    }

    private JwtRequestPostProcessor employee() {
        return keycloakJwt(employeeId, "employee");
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

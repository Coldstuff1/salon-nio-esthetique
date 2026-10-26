package ca.nio.backend.services;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.net.URI;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.jupiter.api.Test;

import com.nimbusds.jose.JOSEObjectType;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.crypto.ECDSAVerifier;
import com.nimbusds.jwt.SignedJWT;

class WebPushClientTest {

    // RFC 8291, section 5 and appendix A
    static final String PLAINTEXT = "When I grow up, I want to be a watermelon";
    static final String AS_PUBLIC = "BP4z9KsN6nGRTbVYI_c7VJSPQTBtkgcy27mlmlMoZIIgDll6e3vCYLocInmYWAmS6TlzAC8wEqKK6PBru3jl7A8";
    static final String AS_PRIVATE = "yfWPiYE-n46HLnH0KqZOF1fJJU3MYrct3AELtAQ-oRw";
    static final String UA_PUBLIC = "BCVxsr7N_eNgVRqvHtD0zTZsEc6-VV-JvLexhqUzORcxaOzi6-AYWXvTBHm4bjyPjs7Vd8pZGH6SRpkNtoIAiw4";
    static final String UA_PRIVATE = "q1dXpw3UpT5VOmu_cf_v6ih07Aems3njxI-JWgLcM94";
    static final String SALT = "DGv6ra1nlYgDCS1FRnbzlw";
    static final String AUTH = "BTBZMqHH6r4Tts7J_aSIgg";
    static final String HEADER = "DGv6ra1nlYgDCS1FRnbzlwAAEABBBP4z9KsN6nGRTbVYI_c7VJSPQTBtkgcy27mlmlMoZIIgDll6e3vCYLocIn"
            + "mYWAmS6TlzAC8wEqKK6PBru3jl7A8";
    static final String CIPHERTEXT = "8pfeW0KbunFT06SuDKoJH9Ql87S1QUrdirN6GcG7sFz1y1sqLgVi1VhjVkHsUoEsbI_0LpXMuGvnzQ";

    // the development pair from application.yaml
    static final String DEV_PUBLIC = "BLRMwTrjTibku8zYH1brqvSmrx4pQ_1k5zL7GCAD7cPuAL_A-757m242MYvnu-eIzBPlBXW5f73ubOYhUM1FY-4";
    static final String DEV_PRIVATE = "xckGOvQAv_t7eiZXitzGhJOZ0t80YFmHsVKdvzc2s6I";

    @Test
    void encryptsLikeTheRfcExample() {
        byte[] message = WebPushClient.encrypt(PLAINTEXT.getBytes(StandardCharsets.UTF_8),
                WebPushClient.decode(UA_PUBLIC), WebPushClient.decode(AUTH), pair(AS_PUBLIC, AS_PRIVATE),
                WebPushClient.decode(SALT));

        assertThat(message).isEqualTo(rfcMessage());
    }

    @Test
    void theRfcExampleDecryptsInTheTestBrowser() throws Exception {
        TestBrowser browser = new TestBrowser(pair(UA_PUBLIC, UA_PRIVATE), WebPushClient.decode(AUTH));

        assertThat(new String(browser.decrypt(rfcMessage()), StandardCharsets.UTF_8)).isEqualTo(PLAINTEXT);
    }

    @Test
    void signsATokenForThePushServicesOrigin() throws Exception {
        WebPushClient client = new WebPushClient(properties(DEV_PUBLIC, DEV_PRIVATE));
        Instant now = Instant.now().truncatedTo(ChronoUnit.SECONDS);

        String authorization = client.vapidAuthorization(
                URI.create("https://fcm.googleapis.com/fcm/send/abc:def"), now);

        Matcher matcher = Pattern.compile("vapid t=([^,]+), k=(.+)").matcher(authorization);
        assertThat(matcher.matches()).as(authorization).isTrue();
        assertThat(matcher.group(2)).isEqualTo(DEV_PUBLIC);
        SignedJWT token = SignedJWT.parse(matcher.group(1));
        assertThat(token.getHeader().getAlgorithm()).isEqualTo(JWSAlgorithm.ES256);
        assertThat(token.getHeader().getType()).isEqualTo(JOSEObjectType.JWT);
        assertThat(token.verify(new ECDSAVerifier(WebPushClient.publicKey(WebPushClient.decode(DEV_PUBLIC)))))
                .isTrue();
        // a plain string: some push services refuse an array
        assertThat(token.getPayload().toJSONObject().get("aud")).isEqualTo("https://fcm.googleapis.com");
        assertThat(token.getJWTClaimsSet().getExpirationTime().toInstant()).isEqualTo(now.plus(Duration.ofHours(12)));
        assertThat(token.getJWTClaimsSet().getSubject()).isEqualTo("mailto:admin@example.com");
    }

    @Test
    void refusesKeysThatArentAPair() {
        assertThatThrownBy(() -> new WebPushClient(properties(DEV_PUBLIC, AS_PRIVATE)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("doesn't match");
    }

    @Test
    void acceptsOnlyPointsOnTheCurve() {
        byte[] offCurve = WebPushClient.decode(UA_PUBLIC);
        offCurve[64] ^= 1;

        assertThat(WebPushClient.isValidPublicKey(WebPushClient.decode(UA_PUBLIC))).isTrue();
        assertThat(WebPushClient.isValidPublicKey(offCurve)).isFalse();
        assertThat(WebPushClient.isValidPublicKey(new byte[33])).isFalse();
    }

    private static byte[] rfcMessage() {
        byte[] header = WebPushClient.decode(HEADER);
        byte[] ciphertext = WebPushClient.decode(CIPHERTEXT);
        assertThat(header).hasSize(WebPushClient.HEADER_SIZE);
        return ByteBuffer.allocate(header.length + ciphertext.length).put(header).put(ciphertext).array();
    }

    private static KeyPair pair(String publicKey, String privateKey) {
        return new KeyPair(WebPushClient.publicKey(WebPushClient.decode(publicKey)),
                WebPushClient.privateKey(WebPushClient.decode(privateKey)));
    }

    private static NotificationProperties properties(String publicKey, String privateKey) {
        return new NotificationProperties(URI.create("http://localhost:5173"), "no-reply@localhost", "Salon",
                Duration.ofSeconds(15), new NotificationProperties.Push(publicKey, privateKey,
                "mailto:admin@example.com", Duration.ofHours(24), List.of()),
                new NotificationProperties.Jobs("-", Duration.ofHours(24), LocalTime.of(7, 0), Duration.ofHours(2)));
    }

}

package ca.nio.backend.services;

import java.io.IOException;
import java.io.InterruptedIOException;
import java.math.BigInteger;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.AlgorithmParameters;
import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.SecureRandom;
import java.security.Signature;
import java.security.interfaces.ECPrivateKey;
import java.security.interfaces.ECPublicKey;
import java.security.spec.ECGenParameterSpec;
import java.security.spec.ECParameterSpec;
import java.security.spec.ECPoint;
import java.security.spec.ECPrivateKeySpec;
import java.security.spec.ECPublicKeySpec;
import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.Base64;
import java.util.Date;

import javax.crypto.Cipher;
import javax.crypto.KDF;
import javax.crypto.KeyAgreement;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.HKDFParameterSpec;
import javax.crypto.spec.SecretKeySpec;

import org.springframework.stereotype.Component;
import org.springframework.util.Assert;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JOSEObjectType;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.ECDSASigner;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;

import ca.nio.backend.model.PushSubscription;

/**
 * Sends one Web Push message to one browser's push service: the payload encrypted for that browser (RFC 8291,
 * {@code aes128gcm} from RFC 8188) and signed for with the salon's VAPID key (RFC 8292). No library does this
 * for us: the maintained ones drag in old HTTP and BouncyCastle jars, and the JDK has every primitive needed.
 */
@Component
public class WebPushClient {

    /** The one record size used; a payload must fit in a single record. */
    static final int RECORD_SIZE = 4096;
    /** Salt, record size, key id length and key id (the server's public key): RFC 8188 section 2.1. */
    static final int HEADER_SIZE = 16 + 4 + 1 + 65;
    /** Largest plaintext that fits: one record, less the padding delimiter and the AES-GCM tag. */
    public static final int MAX_PAYLOAD = RECORD_SIZE - HEADER_SIZE - 1 - 16;

    /** The VAPID token's lifetime; push services refuse more than 24 hours. */
    static final Duration TOKEN_LIFETIME = Duration.ofHours(12);

    private static final ECParameterSpec P256 = p256();
    private static final Base64.Encoder BASE64URL = Base64.getUrlEncoder().withoutPadding();
    private static final Base64.Decoder BASE64URL_DECODER = Base64.getUrlDecoder();
    private static final SecureRandom RANDOM = new SecureRandom();

    private final ECPublicKey publicKey;
    private final ECPrivateKey privateKey;
    private final String encodedPublicKey;
    private final String subject;
    private final Duration ttl;
    private final HttpClient http = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            // a push service answers directly; a redirect would send the message somewhere it wasn't meant to go
            .followRedirects(HttpClient.Redirect.NEVER)
            .build();

    public WebPushClient(NotificationProperties properties) {
        NotificationProperties.Push push = properties.push();
        Assert.hasText(push.publicKey(), "salon.notifications.push.public-key is required");
        Assert.hasText(push.privateKey(), "salon.notifications.push.private-key is required");
        this.publicKey = publicKey(decode(push.publicKey()));
        this.privateKey = privateKey(decode(push.privateKey()));
        this.encodedPublicKey = push.publicKey();
        this.subject = push.subject();
        this.ttl = push.ttl();
        checkKeysMatch(publicKey, privateKey);
    }

    /** What browsers subscribe with ({@code applicationServerKey}). */
    public String publicKey() {
        return encodedPublicKey;
    }

    /**
     * Posts the payload to the browser's push service.
     *
     * @return the push service's status: 201 when it took the message, 404 or 410 when the browser unsubscribed
     */
    public int send(PushSubscription subscription, byte[] payload) throws IOException {
        Assert.isTrue(payload.length <= MAX_PAYLOAD, "Push payload too large: " + payload.length + " bytes");
        URI endpoint = URI.create(subscription.getEndpoint());
        byte[] body = encrypt(payload, decode(subscription.getP256dh()), decode(subscription.getAuth()),
                newKeyPair(), randomBytes(16));
        HttpRequest request = HttpRequest.newBuilder(endpoint)
                .timeout(Duration.ofSeconds(10))
                .header("Authorization", vapidAuthorization(endpoint, Instant.now()))
                .header("TTL", Long.toString(ttl.toSeconds()))
                .header("Content-Encoding", "aes128gcm")
                .header("Content-Type", "application/octet-stream")
                .POST(HttpRequest.BodyPublishers.ofByteArray(body))
                .build();
        try {
            return http.send(request, HttpResponse.BodyHandlers.discarding()).statusCode();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new InterruptedIOException("Interrupted while sending to " + endpoint.getHost());
        }
    }

    /** {@code vapid t=<token>, k=<public key>}, the token naming the push service's origin as its audience. */
    String vapidAuthorization(URI endpoint, Instant now) {
        String audience = endpoint.getScheme() + "://" + endpoint.getRawAuthority();
        JWTClaimsSet claims = new JWTClaimsSet.Builder()
                .audience(audience)
                .expirationTime(Date.from(now.plus(TOKEN_LIFETIME)))
                .subject(subject)
                .build();
        SignedJWT token = new SignedJWT(new JWSHeader.Builder(JWSAlgorithm.ES256).type(JOSEObjectType.JWT).build(),
                claims);
        try {
            token.sign(new ECDSASigner(privateKey));
        } catch (JOSEException e) {
            throw new IllegalStateException("Couldn't sign the VAPID token", e);
        }
        return "vapid t=" + token.serialize() + ", k=" + encodedPublicKey;
    }

    /**
     * The message body: an RFC 8188 header naming the salt and the server's one-off public key, then the payload
     * encrypted in a single record. The salt and key pair are fresh for every message; tests pass fixed ones.
     *
     * @param uaPublic   the browser's public key ({@code p256dh}), uncompressed
     * @param authSecret the browser's authentication secret ({@code auth})
     */
    static byte[] encrypt(byte[] plaintext, byte[] uaPublic, byte[] authSecret, KeyPair serverKeys, byte[] salt) {
        try {
            byte[] asPublic = uncompressed((ECPublicKey) serverKeys.getPublic());
            KeyAgreement agreement = KeyAgreement.getInstance("ECDH");
            agreement.init(serverKeys.getPrivate());
            agreement.doPhase(publicKey(uaPublic), true);
            byte[] ecdhSecret = agreement.generateSecret();

            KDF hkdf = KDF.getInstance("HKDF-SHA256");
            byte[] keyInfo = concat("WebPush: info\0".getBytes(StandardCharsets.US_ASCII), uaPublic, asPublic);
            byte[] ikm = hkdf.deriveData(HKDFParameterSpec.ofExtract()
                    .addIKM(ecdhSecret).addSalt(authSecret).thenExpand(keyInfo, 32));
            byte[] cek = hkdf.deriveData(HKDFParameterSpec.ofExtract().addIKM(ikm).addSalt(salt)
                    .thenExpand("Content-Encoding: aes128gcm\0".getBytes(StandardCharsets.US_ASCII), 16));
            byte[] nonce = hkdf.deriveData(HKDFParameterSpec.ofExtract().addIKM(ikm).addSalt(salt)
                    .thenExpand("Content-Encoding: nonce\0".getBytes(StandardCharsets.US_ASCII), 12));

            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(cek, "AES"), new GCMParameterSpec(128, nonce));
            // 0x02 marks the last (here, only) record; no further padding
            byte[] ciphertext = cipher.doFinal(concat(plaintext, new byte[] {2}));

            return ByteBuffer.allocate(HEADER_SIZE + ciphertext.length)
                    .put(salt)
                    .putInt(RECORD_SIZE)
                    .put((byte) asPublic.length)
                    .put(asPublic)
                    .put(ciphertext)
                    .array();
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("Couldn't encrypt the push message", e);
        }
    }

    /** True for a well-formed browser key: an uncompressed point on P-256. */
    public static boolean isValidPublicKey(byte[] uncompressed) {
        try {
            KeyAgreement agreement = KeyAgreement.getInstance("ECDH");
            agreement.init(newKeyPair().getPrivate());
            // the provider checks the point is on the curve
            agreement.doPhase(publicKey(uncompressed), true);
            return true;
        } catch (GeneralSecurityException | IllegalArgumentException e) {
            return false;
        }
    }

    static byte[] decode(String base64url) {
        return BASE64URL_DECODER.decode(base64url);
    }

    static String encode(byte[] bytes) {
        return BASE64URL.encodeToString(bytes);
    }

    static ECPublicKey publicKey(byte[] uncompressed) {
        if (uncompressed.length != 65 || uncompressed[0] != 4) {
            throw new IllegalArgumentException("Not an uncompressed P-256 point");
        }
        ECPoint point = new ECPoint(new BigInteger(1, Arrays.copyOfRange(uncompressed, 1, 33)),
                new BigInteger(1, Arrays.copyOfRange(uncompressed, 33, 65)));
        try {
            return (ECPublicKey) KeyFactory.getInstance("EC").generatePublic(new ECPublicKeySpec(point, P256));
        } catch (GeneralSecurityException e) {
            throw new IllegalArgumentException("Not a P-256 public key", e);
        }
    }

    static ECPrivateKey privateKey(byte[] scalar) {
        try {
            return (ECPrivateKey) KeyFactory.getInstance("EC")
                    .generatePrivate(new ECPrivateKeySpec(new BigInteger(1, scalar), P256));
        } catch (GeneralSecurityException e) {
            throw new IllegalArgumentException("Not a P-256 private key", e);
        }
    }

    /** {@code 0x04 || x || y}, each coordinate on 32 bytes. */
    static byte[] uncompressed(ECPublicKey key) {
        return concat(new byte[] {4}, fixed32(key.getW().getAffineX()), fixed32(key.getW().getAffineY()));
    }

    private static byte[] fixed32(BigInteger value) {
        byte[] bytes = value.toByteArray();
        byte[] out = new byte[32];
        int length = Math.min(bytes.length, 32);
        System.arraycopy(bytes, bytes.length - length, out, 32 - length, length);
        return out;
    }

    private static KeyPair newKeyPair() {
        try {
            KeyPairGenerator generator = KeyPairGenerator.getInstance("EC");
            generator.initialize(new ECGenParameterSpec("secp256r1"), RANDOM);
            return generator.generateKeyPair();
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException(e);
        }
    }

    private static byte[] randomBytes(int length) {
        byte[] bytes = new byte[length];
        RANDOM.nextBytes(bytes);
        return bytes;
    }

    private static byte[] concat(byte[]... parts) {
        ByteBuffer buffer = ByteBuffer.allocate(Arrays.stream(parts).mapToInt(part -> part.length).sum());
        for (byte[] part : parts) {
            buffer.put(part);
        }
        return buffer.array();
    }

    /** Fails startup when the two keys aren't a pair, instead of every push service refusing every message. */
    private static void checkKeysMatch(ECPublicKey publicKey, ECPrivateKey privateKey) {
        try {
            byte[] probe = "vapid".getBytes(StandardCharsets.US_ASCII);
            Signature signer = Signature.getInstance("SHA256withECDSA");
            signer.initSign(privateKey);
            signer.update(probe);
            byte[] signature = signer.sign();
            Signature verifier = Signature.getInstance("SHA256withECDSA");
            verifier.initVerify(publicKey);
            verifier.update(probe);
            if (!verifier.verify(signature)) {
                throw new IllegalStateException("salon.notifications.push.public-key doesn't match the private key");
            }
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("Invalid VAPID keys", e);
        }
    }

    private static ECParameterSpec p256() {
        try {
            AlgorithmParameters parameters = AlgorithmParameters.getInstance("EC");
            parameters.init(new ECGenParameterSpec("secp256r1"));
            return parameters.getParameterSpec(ECParameterSpec.class);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException(e);
        }
    }

}

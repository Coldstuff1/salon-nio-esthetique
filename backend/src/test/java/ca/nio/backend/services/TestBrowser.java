package ca.nio.backend.services;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.SecureRandom;
import java.security.interfaces.ECPublicKey;
import java.security.spec.ECGenParameterSpec;
import java.util.Arrays;

import javax.crypto.Cipher;
import javax.crypto.KDF;
import javax.crypto.KeyAgreement;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.HKDFParameterSpec;
import javax.crypto.spec.SecretKeySpec;

/**
 * The browser's side of Web Push in tests: keys to subscribe with, and decrypting what the push service delivers
 * (RFC 8291, written out again here rather than reusing {@link WebPushClient}'s encryption).
 */
public class TestBrowser {

    private final KeyPair keys;
    private final byte[] authSecret;

    public TestBrowser() {
        try {
            KeyPairGenerator generator = KeyPairGenerator.getInstance("EC");
            generator.initialize(new ECGenParameterSpec("secp256r1"));
            this.keys = generator.generateKeyPair();
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException(e);
        }
        this.authSecret = new byte[16];
        new SecureRandom().nextBytes(authSecret);
    }

    TestBrowser(KeyPair keys, byte[] authSecret) {
        this.keys = keys;
        this.authSecret = authSecret;
    }

    public String p256dh() {
        return WebPushClient.encode(WebPushClient.uncompressed((ECPublicKey) keys.getPublic()));
    }

    public String auth() {
        return WebPushClient.encode(authSecret);
    }

    /** The plaintext of an {@code aes128gcm} message made of one record. */
    public byte[] decrypt(byte[] message) throws GeneralSecurityException {
        ByteBuffer buffer = ByteBuffer.wrap(message);
        byte[] salt = new byte[16];
        buffer.get(salt);
        int recordSize = buffer.getInt();
        byte[] asPublic = new byte[buffer.get() & 0xff];
        buffer.get(asPublic);
        byte[] ciphertext = new byte[buffer.remaining()];
        buffer.get(ciphertext);
        if (ciphertext.length > recordSize) {
            throw new IllegalArgumentException("More than one record");
        }

        KeyAgreement agreement = KeyAgreement.getInstance("ECDH");
        agreement.init(keys.getPrivate());
        agreement.doPhase(WebPushClient.publicKey(asPublic), true);
        byte[] uaPublic = WebPushClient.decode(p256dh());
        byte[] keyInfo = ByteBuffer.allocate(14 + 65 + 65)
                .put("WebPush: info\0".getBytes(StandardCharsets.US_ASCII)).put(uaPublic).put(asPublic).array();
        KDF hkdf = KDF.getInstance("HKDF-SHA256");
        byte[] ikm = hkdf.deriveData(HKDFParameterSpec.ofExtract().addIKM(agreement.generateSecret())
                .addSalt(authSecret).thenExpand(keyInfo, 32));
        byte[] cek = hkdf.deriveData(HKDFParameterSpec.ofExtract().addIKM(ikm).addSalt(salt)
                .thenExpand("Content-Encoding: aes128gcm\0".getBytes(StandardCharsets.US_ASCII), 16));
        byte[] nonce = hkdf.deriveData(HKDFParameterSpec.ofExtract().addIKM(ikm).addSalt(salt)
                .thenExpand("Content-Encoding: nonce\0".getBytes(StandardCharsets.US_ASCII), 12));

        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.DECRYPT_MODE, new SecretKeySpec(cek, "AES"), new GCMParameterSpec(128, nonce));
        byte[] padded = cipher.doFinal(ciphertext);
        int end = padded.length - 1;
        while (end >= 0 && padded[end] == 0) {
            end--;
        }
        if (end < 0 || padded[end] != 2) {
            throw new IllegalArgumentException("No last-record delimiter");
        }
        return Arrays.copyOf(padded, end);
    }

}

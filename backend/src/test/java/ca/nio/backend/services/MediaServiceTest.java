package ca.nio.backend.services;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.Test;

class MediaServiceTest {

    @Test
    void recognizesEachAcceptedFormatByItsLeadingBytes() {
        assertThat(MediaService.hasSignature("image/jpeg", bytes(0xFF, 0xD8, 0xFF, 0xE0))).isTrue();
        assertThat(MediaService.hasSignature("image/png", bytes(0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A))).isTrue();
        assertThat(MediaService.hasSignature("image/webp", ascii("RIFF\0\0\0\0WEBPVP8 "))).isTrue();
        assertThat(MediaService.hasSignature("video/mp4", ascii("\0\0\0\u0018ftypmp42"))).isTrue();
        assertThat(MediaService.hasSignature("video/webm", bytes(0x1A, 0x45, 0xDF, 0xA3))).isTrue();
    }

    @Test
    void rejectsOtherBytesUnderAnAcceptedType() {
        assertThat(MediaService.hasSignature("image/png", bytes(0xFF, 0xD8, 0xFF, 0xE0))).isFalse();
        assertThat(MediaService.hasSignature("image/jpeg", ascii("<svg onload=alert(1)>"))).isFalse();
        assertThat(MediaService.hasSignature("image/webp", ascii("RIFF\0\0\0\0WAVEfmt "))).isFalse();
        assertThat(MediaService.hasSignature("image/jpeg", bytes(0xFF))).isFalse();
        assertThat(MediaService.hasSignature("image/gif", ascii("GIF89a"))).isFalse();
    }

    private static byte[] bytes(int... values) {
        byte[] bytes = new byte[values.length];
        for (int i = 0; i < values.length; i++) {
            bytes[i] = (byte) values[i];
        }
        return bytes;
    }

    private static byte[] ascii(String text) {
        return text.getBytes(StandardCharsets.ISO_8859_1);
    }

}

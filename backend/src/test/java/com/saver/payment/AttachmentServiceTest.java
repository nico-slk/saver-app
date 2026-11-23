package com.saver.payment;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

class AttachmentServiceTest {

    @Test
    void typeComesFromTheContentNotTheName() {
        assertThat(AttachmentService.sniff("%PDF-1.7".getBytes(StandardCharsets.US_ASCII))).isEqualTo("application/pdf");
        assertThat(AttachmentService.sniff(new byte[]{(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, 0})).isEqualTo("image/jpeg");
        assertThat(AttachmentService.sniff("RIFF0000WEBPVP8 ".getBytes(StandardCharsets.US_ASCII))).isEqualTo("image/webp");
        assertThat(AttachmentService.sniff("<html>".getBytes(StandardCharsets.US_ASCII))).isNull();
        assertThat(AttachmentService.sniff(new byte[0])).isNull();
    }

    @Test
    void fileNamesLoseFoldersAndControlCharacters() {
        assertThat(AttachmentService.cleanName("C:\\Users\\nico\\factura luz.pdf", "application/pdf")).isEqualTo("factura luz.pdf");
        assertThat(AttachmentService.cleanName("../../etc/passwd\"\n", "image/png")).isEqualTo("passwd");
        assertThat(AttachmentService.cleanName(null, "image/jpeg")).isEqualTo("comprobante.jpeg");
    }
}

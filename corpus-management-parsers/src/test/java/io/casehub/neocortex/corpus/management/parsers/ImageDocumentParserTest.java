package io.casehub.neocortex.corpus.management.parsers;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

class ImageDocumentParserTest {

    private final ImageDocumentParser parser = new ImageDocumentParser(null);

    @Test
    void supportedMediaTypes() {
        assertThat(parser.supportedMediaTypes()).containsExactlyInAnyOrder(
            "image/png", "image/jpeg", "image/tiff", "image/bmp", "image/gif");
    }

    @Test
    void parseImageWithoutTesseract() {
        byte[] minimalPng = createMinimalPng();
        var result = parser.parse(new ByteArrayInputStream(minimalPng),
            "photo.png", DocumentParserConfig.defaults());

        assertThat(result.positionalMarkdown()).contains("#@ page: 1");
        assertThat(result.positionalMarkdown()).contains("#@ panel: main");
        assertThat(result.layout().name()).isEqualTo("single");
        assertThat(result.extractedMetadata()).containsEntry("source_format", "image");
        assertThat(result.extractedMetadata()).containsEntry("mime_type", "image/png");
        assertThat(result.imageRegions()).hasSize(1);
    }

    @Test
    void detectsMimeTypeFromExtension() {
        byte[] png = createMinimalPng();

        var pngResult = parser.parse(new ByteArrayInputStream(png), "test.png", DocumentParserConfig.defaults());
        assertThat(pngResult.extractedMetadata()).containsEntry("mime_type", "image/png");

        var jpgResult = parser.parse(new ByteArrayInputStream(png), "test.jpg", DocumentParserConfig.defaults());
        assertThat(jpgResult.extractedMetadata()).containsEntry("mime_type", "image/jpeg");

        var tiffResult = parser.parse(new ByteArrayInputStream(png), "test.tiff", DocumentParserConfig.defaults());
        assertThat(tiffResult.extractedMetadata()).containsEntry("mime_type", "image/tiff");
    }

    @Test
    void imageDescriberCalledWhenEnabled() {
        AtomicInteger calls = new AtomicInteger(0);

        var parserWithDescriber = new ImageDocumentParser((imageBytes, mimeType) -> {
            calls.incrementAndGet();
            assertThat(imageBytes).isNotEmpty();
            assertThat(mimeType).isEqualTo("image/png");
            return "A minimal red pixel image";
        });

        byte[] png = createMinimalPng();
        var config = new DocumentParserConfig(true, true, false, Integer.MAX_VALUE);
        var result = parserWithDescriber.parse(new ByteArrayInputStream(png), "test.png", config);

        assertThat(calls.get()).isEqualTo(1);
        assertThat(result.positionalMarkdown()).contains("#@ describe: \"A minimal red pixel image\"");
        assertThat(result.imageRegions().get(0).llmDescription()).isEqualTo("A minimal red pixel image");
    }

    @Test
    void imageDescriberNotCalledWhenDisabled() {
        AtomicInteger calls = new AtomicInteger(0);

        var parserWithDescriber = new ImageDocumentParser((imageBytes, mimeType) -> {
            calls.incrementAndGet();
            return "Should not appear";
        });

        byte[] png = createMinimalPng();
        var result = parserWithDescriber.parse(new ByteArrayInputStream(png), "test.png",
            DocumentParserConfig.defaults());

        assertThat(calls.get()).isZero();
        assertThat(result.positionalMarkdown()).doesNotContain("#@ describe:");
    }

    @Test
    void ocrDisabledSkipsExtraction() {
        byte[] png = createMinimalPng();
        var config = new DocumentParserConfig(false, false, false, Integer.MAX_VALUE);
        var result = parser.parse(new ByteArrayInputStream(png), "test.png", config);

        assertThat(result.positionalMarkdown()).doesNotContain("#@ ocr:");
    }

    @Test
    void imageRegionCapturesBytes() {
        byte[] png = createMinimalPng();
        var result = parser.parse(new ByteArrayInputStream(png), "test.png",
            DocumentParserConfig.defaults());

        assertThat(result.imageRegions()).hasSize(1);
        assertThat(result.imageRegions().get(0).imageBytes()).isEqualTo(png);
        assertThat(result.imageRegions().get(0).panelId()).isEqualTo("main");
        assertThat(result.imageRegions().get(0).pageNumber()).isEqualTo(1);
    }

    private static byte[] createMinimalPng() {
        return new byte[]{
            (byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A,
            0x00, 0x00, 0x00, 0x0D, 0x49, 0x48, 0x44, 0x52,
            0x00, 0x00, 0x00, 0x01, 0x00, 0x00, 0x00, 0x01,
            0x08, 0x02, 0x00, 0x00, 0x00, (byte) 0x90, 0x77, 0x53,
            (byte) 0xDE, 0x00, 0x00, 0x00, 0x0C, 0x49, 0x44, 0x41,
            0x54, 0x08, (byte) 0xD7, 0x63, (byte) 0xF8, (byte) 0xCF,
            (byte) 0xC0, 0x00, 0x00, 0x00, 0x03, 0x00, 0x01, 0x36,
            (byte) 0x28, 0x05, (byte) 0x9B, 0x00, 0x00, 0x00, 0x00,
            0x49, 0x45, 0x4E, 0x44, (byte) 0xAE, 0x42, 0x60, (byte) 0x82
        };
    }
}

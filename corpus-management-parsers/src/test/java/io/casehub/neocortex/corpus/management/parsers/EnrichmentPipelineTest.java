package io.casehub.neocortex.corpus.management.parsers;

import io.casehub.neocortex.corpus.management.ImageDescriber;
import io.casehub.neocortex.corpus.management.LayoutClassification;
import io.casehub.neocortex.corpus.management.LayoutClassifier;
import io.casehub.neocortex.corpus.management.PanelDefinition;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

class EnrichmentPipelineTest {

    @Test
    void layoutClassifierOverridesPdfBoxHeuristics() throws Exception {
        AtomicInteger classifierCalls = new AtomicInteger(0);

        LayoutClassifier stubClassifier = (imageBytes, mimeType) -> {
            classifierCalls.incrementAndGet();
            assertThat(imageBytes).isNotEmpty();
            assertThat(mimeType).isEqualTo("image/png");
            return new LayoutClassification("header-body-footer", List.of(
                new PanelDefinition("header", 0.0, 0.9, 1.0, 0.1),
                new PanelDefinition("body", 0.0, 0.1, 1.0, 0.78),
                new PanelDefinition("footer", 0.0, 0.0, 1.0, 0.08)));
        };

        var parser = new PdfDocumentParser(stubClassifier, null);
        byte[] pdf = createSimplePdf("Title", "Body text.");
        var config = new DocumentParserConfig(true, false, true, Integer.MAX_VALUE);

        ParseResult result = parser.parse(new ByteArrayInputStream(pdf), "test.pdf", config);

        assertThat(classifierCalls.get()).isEqualTo(1);
        assertThat(result.layout().name()).isEqualTo("header-body-footer");
        assertThat(result.layout().panels()).hasSize(3);
        assertThat(result.layout().panels().stream().map(PanelDefinition::id).toList())
            .containsExactly("header", "body", "footer");
    }

    @Test
    void layoutClassifierNotCalledWhenDisabled() throws Exception {
        AtomicInteger classifierCalls = new AtomicInteger(0);

        LayoutClassifier stubClassifier = (imageBytes, mimeType) -> {
            classifierCalls.incrementAndGet();
            return new LayoutClassification("3-column", List.of());
        };

        var parser = new PdfDocumentParser(stubClassifier, null);
        byte[] pdf = createSimplePdf("Title", "Body.");
        var config = DocumentParserConfig.defaults();

        parser.parse(new ByteArrayInputStream(pdf), "test.pdf", config);

        assertThat(classifierCalls.get()).isZero();
    }

    @Test
    void layoutClassifierAbsentFallsBackToHeuristics() throws Exception {
        var parser = new PdfDocumentParser(null, null);
        byte[] pdf = createSimplePdf("Title", "Body text.");
        var config = new DocumentParserConfig(true, false, true, Integer.MAX_VALUE);

        ParseResult result = parser.parse(new ByteArrayInputStream(pdf), "test.pdf", config);

        assertThat(result.layout().name()).isEqualTo("single");
    }

    @Test
    void imageDescriberEnrichesDocxOutput() throws Exception {
        AtomicInteger describerCalls = new AtomicInteger(0);

        ImageDescriber stubDescriber = (imageBytes, mimeType) -> {
            describerCalls.incrementAndGet();
            assertThat(imageBytes).isNotEmpty();
            return "A professional headshot photograph";
        };

        var parser = new DocxDocumentParser(stubDescriber);
        byte[] docx = createDocxWithImage();
        var config = new DocumentParserConfig(true, true, false, Integer.MAX_VALUE);

        ParseResult result = parser.parse(new ByteArrayInputStream(docx), "photo.docx", config);

        assertThat(describerCalls.get()).isGreaterThanOrEqualTo(1);
        assertThat(result.positionalMarkdown()).contains("#@ describe: \"A professional headshot photograph\"");
        assertThat(result.imageRegions()).isNotEmpty();
        assertThat(result.imageRegions().get(0).llmDescription()).isEqualTo("A professional headshot photograph");
    }

    @Test
    void imageDescriberNotCalledWhenDisabled() throws Exception {
        AtomicInteger describerCalls = new AtomicInteger(0);

        ImageDescriber stubDescriber = (imageBytes, mimeType) -> {
            describerCalls.incrementAndGet();
            return "Should not appear";
        };

        var parser = new DocxDocumentParser(stubDescriber);
        byte[] docx = createDocxWithImage();
        var config = DocumentParserConfig.defaults();

        ParseResult result = parser.parse(new ByteArrayInputStream(docx), "photo.docx", config);

        assertThat(describerCalls.get()).isZero();
        assertThat(result.positionalMarkdown()).doesNotContain("#@ describe:");
    }

    @Test
    void imageDescriberAbsentProducesNoDescription() throws Exception {
        var parser = new DocxDocumentParser(null);
        byte[] docx = createDocxWithImage();
        var config = new DocumentParserConfig(true, true, false, Integer.MAX_VALUE);

        ParseResult result = parser.parse(new ByteArrayInputStream(docx), "photo.docx", config);

        assertThat(result.positionalMarkdown()).doesNotContain("#@ describe:");
    }

    private static byte[] createSimplePdf(String title, String body) throws IOException {
        try (PDDocument doc = new PDDocument()) {
            PDPage page = new PDPage(PDRectangle.LETTER);
            doc.addPage(page);
            try (PDPageContentStream cs = new PDPageContentStream(doc, page)) {
                cs.beginText();
                cs.setFont(PDType1Font.HELVETICA_BOLD, 16);
                cs.newLineAtOffset(72, 700);
                cs.showText(title);
                cs.setFont(PDType1Font.HELVETICA, 12);
                cs.newLineAtOffset(0, -30);
                cs.showText(body);
                cs.endText();
            }
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            doc.save(out);
            return out.toByteArray();
        }
    }

    private static byte[] createDocxWithImage() throws IOException {
        try (var doc = new org.apache.poi.xwpf.usermodel.XWPFDocument()) {
            var para = doc.createParagraph();
            para.createRun().setText("Document with image.");

            var imgPara = doc.createParagraph();
            var run = imgPara.createRun();
            byte[] fakeImage = createMinimalPng();
            run.addPicture(new ByteArrayInputStream(fakeImage),
                org.apache.poi.xwpf.usermodel.Document.PICTURE_TYPE_PNG,
                "test.png", 100 * 9525, 100 * 9525);

            ByteArrayOutputStream out = new ByteArrayOutputStream();
            doc.write(out);
            return out.toByteArray();
        } catch (org.apache.poi.openxml4j.exceptions.InvalidFormatException e) {
            throw new IOException(e);
        }
    }

    private static byte[] createMinimalPng() {
        // 1x1 red PNG
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

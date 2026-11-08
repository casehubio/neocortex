package io.casehub.neocortex.corpus.management.parsers;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDType1Font;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class PdfDocumentParserTest {

    private final PdfDocumentParser parser = new PdfDocumentParser(null, null);

    @Test
    void supportedMediaTypes() {
        assertThat(parser.supportedMediaTypes()).isEqualTo(Set.of("application/pdf"));
    }

    @Test
    void parseSingleColumnPdf() throws Exception {
        byte[] pdf = createSingleColumnPdf("Hello World", "This is a test paragraph.");
        var result = parser.parse(new ByteArrayInputStream(pdf), "test.pdf", DocumentParserConfig.defaults());

        assertThat(result.positionalMarkdown()).contains("Hello World");
        assertThat(result.positionalMarkdown()).contains("This is a test paragraph.");
        assertThat(result.positionalMarkdown()).contains("#@ panel: main");
        assertThat(result.positionalMarkdown()).contains("#@ page: 1");
        assertThat(result.layout().name()).isEqualTo("single");
        assertThat(result.layout().pageCount()).isEqualTo(1);
        assertThat(result.layout().pageSize()).isEqualTo(new int[]{612, 792});
        assertThat(result.extractedMetadata()).containsEntry("source_format", "pdf");
    }

    @Test
    void parseMultiPagePdf() throws Exception {
        byte[] pdf = createMultiPagePdf();
        var result = parser.parse(new ByteArrayInputStream(pdf), "multi.pdf", DocumentParserConfig.defaults());

        assertThat(result.layout().pageCount()).isEqualTo(2);
        assertThat(result.positionalMarkdown()).contains("#@ page: 1");
        assertThat(result.positionalMarkdown()).contains("#@ page: 2");
        assertThat(result.positionalMarkdown()).contains("Page one content");
        assertThat(result.positionalMarkdown()).contains("Page two content");
    }

    @Test
    void parseExtractsMetadata() throws Exception {
        byte[] pdf = createSingleColumnPdf("Title", "Body text.");
        var result = parser.parse(new ByteArrayInputStream(pdf), "doc.pdf", DocumentParserConfig.defaults());

        assertThat(result.extractedMetadata()).containsEntry("source_format", "pdf");
        assertThat(result.extractedMetadata()).containsKey("page_count");
    }

    @Test
    void parseTwoColumnPdf() throws Exception {
        byte[] pdf = createTwoColumnPdf();
        var result = parser.parse(new ByteArrayInputStream(pdf), "paper.pdf", DocumentParserConfig.defaults());

        assertThat(result.positionalMarkdown()).contains("#@ panel:");
        assertThat(result.layout().panels()).hasSizeGreaterThanOrEqualTo(1);
    }

    @Test
    void parseRespectsMaxPages() throws Exception {
        byte[] pdf = createMultiPagePdf();
        var config = new DocumentParserConfig(true, false, false, 1);
        var result = parser.parse(new ByteArrayInputStream(pdf), "multi.pdf", config);

        assertThat(result.layout().pageCount()).isEqualTo(1);
        assertThat(result.positionalMarkdown()).doesNotContain("#@ page: 2");
    }

    @Test
    void usesLayoutClassifierWhenProvided() throws Exception {
        var classifier = new io.casehub.neocortex.corpus.management.LayoutClassifier() {
            @Override
            public io.casehub.neocortex.corpus.management.LayoutClassification classify(byte[] pageImage, String mimeType) {
                return new io.casehub.neocortex.corpus.management.LayoutClassification("3-column",
                    java.util.List.of(
                        new io.casehub.neocortex.corpus.management.PanelDefinition("left", 0.0, 0.0, 0.31, 1.0),
                        new io.casehub.neocortex.corpus.management.PanelDefinition("center", 0.345, 0.0, 0.31, 1.0),
                        new io.casehub.neocortex.corpus.management.PanelDefinition("right", 0.69, 0.0, 0.31, 1.0)));
            }
        };
        var parserWithClassifier = new PdfDocumentParser(classifier, null);
        byte[] pdf = createSingleColumnPdf("Title", "Body.");
        var config = new DocumentParserConfig(true, false, true, Integer.MAX_VALUE);
        var result = parserWithClassifier.parse(new ByteArrayInputStream(pdf), "test.pdf", config);

        assertThat(result.layout().name()).isEqualTo("3-column");
        assertThat(result.layout().panels()).hasSize(3);
    }

    private static byte[] createSingleColumnPdf(String title, String body) throws IOException {
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

    private static byte[] createMultiPagePdf() throws IOException {
        try (PDDocument doc = new PDDocument()) {
            for (int i = 1; i <= 2; i++) {
                PDPage page = new PDPage(PDRectangle.LETTER);
                doc.addPage(page);
                try (PDPageContentStream cs = new PDPageContentStream(doc, page)) {
                    cs.beginText();
                    cs.setFont(PDType1Font.HELVETICA, 12);
                    cs.newLineAtOffset(72, 700);
                    cs.showText("Page " + (i == 1 ? "one" : "two") + " content");
                    cs.endText();
                }
            }
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            doc.save(out);
            return out.toByteArray();
        }
    }

    private static byte[] createTwoColumnPdf() throws IOException {
        try (PDDocument doc = new PDDocument()) {
            PDPage page = new PDPage(PDRectangle.LETTER);
            doc.addPage(page);
            try (PDPageContentStream cs = new PDPageContentStream(doc, page)) {
                cs.beginText();
                cs.setFont(PDType1Font.HELVETICA, 11);
                cs.newLineAtOffset(72, 700);
                cs.showText("Left column text here discussing");
                cs.newLineAtOffset(0, -15);
                cs.showText("important research findings.");
                cs.endText();

                cs.beginText();
                cs.setFont(PDType1Font.HELVETICA, 11);
                cs.newLineAtOffset(320, 700);
                cs.showText("Right column with additional");
                cs.newLineAtOffset(0, -15);
                cs.showText("analysis and conclusions.");
                cs.endText();
            }
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            doc.save(out);
            return out.toByteArray();
        }
    }
}

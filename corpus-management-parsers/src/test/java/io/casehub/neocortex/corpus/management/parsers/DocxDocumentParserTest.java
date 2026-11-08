package io.casehub.neocortex.corpus.management.parsers;

import org.apache.poi.xwpf.usermodel.ParagraphAlignment;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFRun;
import org.apache.poi.xwpf.usermodel.XWPFTable;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class DocxDocumentParserTest {

    private final DocxDocumentParser parser = new DocxDocumentParser(null);

    @Test
    void supportedMediaTypes() {
        assertThat(parser.supportedMediaTypes()).isEqualTo(
            Set.of("application/vnd.openxmlformats-officedocument.wordprocessingml.document"));
    }

    @Test
    void parseSimpleDocument() throws Exception {
        byte[] docx = createSimpleDocx("Introduction", "This is the body text of the document.");
        var result = parser.parse(new ByteArrayInputStream(docx), "test.docx", DocumentParserConfig.defaults());

        assertThat(result.positionalMarkdown()).contains("# Introduction");
        assertThat(result.positionalMarkdown()).contains("This is the body text of the document.");
        assertThat(result.positionalMarkdown()).contains("#@ panel: main");
        assertThat(result.positionalMarkdown()).contains("#@ page: 1");
        assertThat(result.layout().name()).isEqualTo("single");
        assertThat(result.extractedMetadata()).containsEntry("source_format", "docx");
    }

    @Test
    void parseDocumentWithTable() throws Exception {
        byte[] docx = createDocxWithTable();
        var result = parser.parse(new ByteArrayInputStream(docx), "table.docx", DocumentParserConfig.defaults());

        assertThat(result.positionalMarkdown()).contains("#@ table:");
        assertThat(result.positionalMarkdown()).contains("|");
        assertThat(result.positionalMarkdown()).contains("Header A");
        assertThat(result.positionalMarkdown()).contains("Cell 1");
    }

    @Test
    void parseDocumentWithMultipleHeadings() throws Exception {
        byte[] docx = createDocxWithHeadings();
        var result = parser.parse(new ByteArrayInputStream(docx), "headings.docx", DocumentParserConfig.defaults());

        assertThat(result.positionalMarkdown()).contains("# Chapter One");
        assertThat(result.positionalMarkdown()).contains("## Section A");
        assertThat(result.positionalMarkdown()).contains("First paragraph.");
        assertThat(result.positionalMarkdown()).contains("Second paragraph.");
    }

    @Test
    void layoutIsSingleForDocx() throws Exception {
        byte[] docx = createSimpleDocx("Title", "Body.");
        var result = parser.parse(new ByteArrayInputStream(docx), "doc.docx", DocumentParserConfig.defaults());

        assertThat(result.layout().name()).isEqualTo("single");
        assertThat(result.layout().panels()).hasSize(1);
        assertThat(result.layout().panels().get(0).id()).isEqualTo("main");
    }

    @Test
    void extractsMetadata() throws Exception {
        byte[] docx = createSimpleDocx("Title", "Body.");
        var result = parser.parse(new ByteArrayInputStream(docx), "doc.docx", DocumentParserConfig.defaults());

        assertThat(result.extractedMetadata()).containsEntry("source_format", "docx");
    }

    private static byte[] createSimpleDocx(String heading, String body) throws IOException {
        try (XWPFDocument doc = new XWPFDocument()) {
            XWPFParagraph title = doc.createParagraph();
            title.setStyle("Heading1");
            XWPFRun titleRun = title.createRun();
            titleRun.setText(heading);
            titleRun.setBold(true);
            titleRun.setFontSize(18);

            XWPFParagraph bodyPara = doc.createParagraph();
            XWPFRun bodyRun = bodyPara.createRun();
            bodyRun.setText(body);

            ByteArrayOutputStream out = new ByteArrayOutputStream();
            doc.write(out);
            return out.toByteArray();
        }
    }

    private static byte[] createDocxWithTable() throws IOException {
        try (XWPFDocument doc = new XWPFDocument()) {
            XWPFParagraph intro = doc.createParagraph();
            intro.createRun().setText("Before the table.");

            XWPFTable table = doc.createTable(2, 2);
            table.getRow(0).getCell(0).setText("Header A");
            table.getRow(0).getCell(1).setText("Header B");
            table.getRow(1).getCell(0).setText("Cell 1");
            table.getRow(1).getCell(1).setText("Cell 2");

            XWPFParagraph after = doc.createParagraph();
            after.createRun().setText("After the table.");

            ByteArrayOutputStream out = new ByteArrayOutputStream();
            doc.write(out);
            return out.toByteArray();
        }
    }

    private static byte[] createDocxWithHeadings() throws IOException {
        try (XWPFDocument doc = new XWPFDocument()) {
            XWPFParagraph h1 = doc.createParagraph();
            h1.setStyle("Heading1");
            h1.createRun().setText("Chapter One");

            XWPFParagraph p1 = doc.createParagraph();
            p1.createRun().setText("First paragraph.");

            XWPFParagraph h2 = doc.createParagraph();
            h2.setStyle("Heading2");
            h2.createRun().setText("Section A");

            XWPFParagraph p2 = doc.createParagraph();
            p2.createRun().setText("Second paragraph.");

            ByteArrayOutputStream out = new ByteArrayOutputStream();
            doc.write(out);
            return out.toByteArray();
        }
    }
}

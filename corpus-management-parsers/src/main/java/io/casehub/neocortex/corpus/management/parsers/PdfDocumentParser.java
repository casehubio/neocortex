package io.casehub.neocortex.corpus.management.parsers;

import io.casehub.neocortex.corpus.management.ImageDescriber;
import io.casehub.neocortex.corpus.management.LayoutClassification;
import io.casehub.neocortex.corpus.management.LayoutClassifier;
import io.casehub.neocortex.corpus.management.PanelDefinition;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.pdfbox.text.TextPosition;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class PdfDocumentParser implements DocumentParser {

    private final LayoutClassifier layoutClassifier;
    private final ImageDescriber imageDescriber;

    public PdfDocumentParser(LayoutClassifier layoutClassifier, ImageDescriber imageDescriber) {
        this.layoutClassifier = layoutClassifier;
        this.imageDescriber = imageDescriber;
    }

    @Override
    public Set<String> supportedMediaTypes() {
        return Set.of("application/pdf");
    }

    @Override
    public ParseResult parse(InputStream content, String sourceRef, DocumentParserConfig config) {
        try (PDDocument doc = PDDocument.load(content)) {
            int totalPages = doc.getNumberOfPages();
            int pagesToProcess = Math.min(totalPages, config.maxPages());

            PDPage firstPage = doc.getPage(0);
            PDRectangle mediaBox = firstPage.getMediaBox();
            int[] pageSize = new int[]{Math.round(mediaBox.getWidth()), Math.round(mediaBox.getHeight())};

            List<PageContent> pages = new ArrayList<>();
            for (int i = 0; i < pagesToProcess; i++) {
                pages.add(extractPage(doc, i, config));
            }

            Layout layout = determineLayout(doc, pages, pagesToProcess, pageSize, config);
            String positionalMarkdown = buildPositionalMarkdown(pages, layout);

            Map<String, String> metadata = new LinkedHashMap<>();
            metadata.put("source_format", "pdf");
            metadata.put("page_count", String.valueOf(pagesToProcess));

            String title = doc.getDocumentInformation().getTitle();
            if (title != null && !title.isBlank()) {
                metadata.put("title", title);
            }
            String author = doc.getDocumentInformation().getAuthor();
            if (author != null && !author.isBlank()) {
                metadata.put("author", author);
            }

            return new ParseResult(positionalMarkdown, layout, metadata, List.of());

        } catch (IOException e) {
            throw new RuntimeException("Failed to parse PDF: " + sourceRef, e);
        }
    }

    private PageContent extractPage(PDDocument doc, int pageIndex, DocumentParserConfig config) throws IOException {
        List<TextBlock> textBlocks = extractTextBlocks(doc, pageIndex);

        PDPage page = doc.getPage(pageIndex);
        float pageWidth = page.getMediaBox().getWidth();
        float pageHeight = page.getMediaBox().getHeight();

        for (TextBlock block : textBlocks) {
            block.normalise(pageWidth, pageHeight);
        }

        return new PageContent(pageIndex + 1, textBlocks);
    }

    private List<TextBlock> extractTextBlocks(PDDocument doc, int pageIndex) throws IOException {
        List<TextBlock> blocks = new ArrayList<>();
        var stripper = new BlockExtractingStripper(pageIndex);
        stripper.setStartPage(pageIndex + 1);
        stripper.setEndPage(pageIndex + 1);
        stripper.getText(doc);
        blocks.addAll(stripper.getBlocks());
        return blocks;
    }

    private Layout determineLayout(PDDocument doc, List<PageContent> pages,
                                    int pageCount, int[] pageSize, DocumentParserConfig config) throws IOException {
        if (config.llmLayoutDetectionEnabled() && layoutClassifier != null) {
            PDFRenderer renderer = new PDFRenderer(doc);
            BufferedImage image = renderer.renderImageWithDPI(0, 150);
            byte[] imageBytes = toBytes(image);
            LayoutClassification classification = layoutClassifier.classify(imageBytes, "image/png");
            return new Layout(classification.layoutName(), classification.panels(), pageCount, pageSize);
        }

        return classifyLayoutHeuristic(pages, pageCount, pageSize);
    }

    private Layout classifyLayoutHeuristic(List<PageContent> pages, int pageCount, int[] pageSize) {
        if (pages.isEmpty() || pages.get(0).blocks().isEmpty()) {
            return new Layout("single", LayoutPresets.resolve("single"), pageCount, pageSize);
        }

        PageContent firstPage = pages.get(0);
        List<TextBlock> blocks = firstPage.blocks();

        double midpoint = 0.5;
        int leftCount = 0;
        int rightCount = 0;
        double gapLeft = 1.0;
        double gapRight = 0.0;

        for (TextBlock block : blocks) {
            double blockCenter = block.nx + block.nw / 2.0;
            if (blockCenter < midpoint) {
                leftCount++;
                gapLeft = Math.min(gapLeft, block.nx + block.nw);
            } else {
                rightCount++;
                gapRight = Math.max(gapRight, block.nx);
            }
        }

        boolean hasTwoColumns = leftCount >= 2 && rightCount >= 2 && (gapRight - gapLeft) > 0.02;

        if (hasTwoColumns) {
            double colBoundary = (gapLeft + gapRight) / 2.0;
            double leftWidth = colBoundary - 0.02;
            double rightStart = colBoundary + 0.02;
            double rightWidth = 1.0 - rightStart;
            List<PanelDefinition> panels = List.of(
                new PanelDefinition("left", 0.0, 0.0, Math.min(leftWidth, 0.99), 1.0),
                new PanelDefinition("right", rightStart, 0.0, Math.min(rightWidth, 0.99), 1.0)
            );
            return new Layout("2-column", panels, pageCount, pageSize);
        }

        return new Layout("single", LayoutPresets.resolve("single"), pageCount, pageSize);
    }

    private String buildPositionalMarkdown(List<PageContent> pages, Layout layout) {
        var sb = new StringBuilder();

        for (PageContent page : pages) {
            sb.append("#@ page: ").append(page.pageNumber()).append('\n');

            if ("single".equals(layout.name()) || layout.panels().size() == 1) {
                sb.append("#@ panel: main\n");
                for (TextBlock block : page.blocks()) {
                    sb.append(block.text.strip()).append('\n');
                }
            } else {
                Map<String, List<TextBlock>> panelBlocks = assignBlocksToPanels(page.blocks(), layout.panels());
                for (PanelDefinition panel : layout.panels()) {
                    List<TextBlock> assigned = panelBlocks.getOrDefault(panel.id(), List.of());
                    if (!assigned.isEmpty()) {
                        sb.append("#@ panel: ").append(panel.id()).append('\n');
                        for (TextBlock block : assigned) {
                            sb.append(block.text.strip()).append('\n');
                        }
                    }
                }
            }
        }

        return sb.toString();
    }

    private Map<String, List<TextBlock>> assignBlocksToPanels(List<TextBlock> blocks, List<PanelDefinition> panels) {
        Map<String, List<TextBlock>> result = new LinkedHashMap<>();
        for (PanelDefinition panel : panels) {
            result.put(panel.id(), new ArrayList<>());
        }

        for (TextBlock block : blocks) {
            double blockCenterX = block.nx + block.nw / 2.0;
            double blockCenterY = block.ny + block.nh / 2.0;

            PanelDefinition bestPanel = panels.get(0);
            double bestDistance = Double.MAX_VALUE;

            for (PanelDefinition panel : panels) {
                double panelCenterX = panel.x() + panel.w() / 2.0;
                double panelCenterY = panel.y() + panel.h() / 2.0;
                double dist = Math.sqrt(Math.pow(blockCenterX - panelCenterX, 2) + Math.pow(blockCenterY - panelCenterY, 2));
                if (dist < bestDistance) {
                    bestDistance = dist;
                    bestPanel = panel;
                }
            }
            result.get(bestPanel.id()).add(block);
        }

        return result;
    }

    private static byte[] toBytes(BufferedImage image) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        ImageIO.write(image, "png", baos);
        return baos.toByteArray();
    }

    static class TextBlock {
        String text;
        float x, y, w, h;
        double nx, ny, nw, nh;

        TextBlock(String text, float x, float y, float w, float h) {
            this.text = text;
            this.x = x;
            this.y = y;
            this.w = w;
            this.h = h;
        }

        void normalise(float pageWidth, float pageHeight) {
            this.nx = x / pageWidth;
            this.ny = y / pageHeight;
            this.nw = w / pageWidth;
            this.nh = h / pageHeight;
        }
    }

    record PageContent(int pageNumber, List<TextBlock> blocks) {}

    private static class BlockExtractingStripper extends PDFTextStripper {

        private final int targetPage;
        private final List<TextBlock> blocks = new ArrayList<>();
        private final StringBuilder currentLine = new StringBuilder();
        private float lineX = Float.MAX_VALUE;
        private float lineY = 0;
        private float lineMaxX = 0;
        private float lineH = 0;

        BlockExtractingStripper(int targetPage) throws IOException {
            this.targetPage = targetPage;
        }

        @Override
        protected void writeString(String text, List<TextPosition> textPositions) throws IOException {
            if (textPositions.isEmpty()) return;

            TextPosition first = textPositions.get(0);
            TextPosition last = textPositions.get(textPositions.size() - 1);

            float newY = first.getY();
            if (currentLine.length() > 0 && Math.abs(newY - lineY) > first.getHeight() * 0.5) {
                flushLine();
            }

            if (currentLine.isEmpty()) {
                lineX = first.getX();
                lineY = newY;
                lineH = first.getHeight();
            }
            lineX = Math.min(lineX, first.getX());
            lineMaxX = Math.max(lineMaxX, last.getX() + last.getWidth());
            lineH = Math.max(lineH, first.getHeight());

            currentLine.append(text);
        }

        @Override
        protected void writeLineSeparator() throws IOException {
            flushLine();
        }

        private void flushLine() {
            if (currentLine.length() > 0) {
                String text = currentLine.toString().strip();
                if (!text.isEmpty()) {
                    blocks.add(new TextBlock(text, lineX, lineY, lineMaxX - lineX, lineH));
                }
                currentLine.setLength(0);
                lineX = Float.MAX_VALUE;
                lineMaxX = 0;
            }
        }

        List<TextBlock> getBlocks() {
            flushLine();
            return blocks;
        }
    }
}

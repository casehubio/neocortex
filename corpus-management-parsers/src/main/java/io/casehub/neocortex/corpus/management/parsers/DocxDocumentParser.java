package io.casehub.neocortex.corpus.management.parsers;

import io.casehub.neocortex.corpus.management.ImageDescriber;
import io.casehub.neocortex.corpus.management.PanelDefinition;
import org.apache.poi.xwpf.usermodel.IBodyElement;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFPicture;
import org.apache.poi.xwpf.usermodel.XWPFRun;
import org.apache.poi.xwpf.usermodel.XWPFTable;
import org.apache.poi.xwpf.usermodel.XWPFTableCell;
import org.apache.poi.xwpf.usermodel.XWPFTableRow;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class DocxDocumentParser implements DocumentParser {

    private final ImageDescriber imageDescriber;

    public DocxDocumentParser(ImageDescriber imageDescriber) {
        this.imageDescriber = imageDescriber;
    }

    @Override
    public Set<String> supportedMediaTypes() {
        return Set.of("application/vnd.openxmlformats-officedocument.wordprocessingml.document");
    }

    @Override
    public ParseResult parse(InputStream content, String sourceRef, DocumentParserConfig config) {
        try (XWPFDocument doc = new XWPFDocument(content)) {
            var sb = new StringBuilder();
            List<ImageRegion> imageRegions = new ArrayList<>();

            sb.append("#@ page: 1\n");
            sb.append("#@ panel: main\n");

            for (IBodyElement element : doc.getBodyElements()) {
                if (element instanceof XWPFParagraph para) {
                    renderParagraph(para, sb, imageRegions, config);
                } else if (element instanceof XWPFTable table) {
                    renderTable(table, sb);
                }
            }

            List<PanelDefinition> panels = LayoutPresets.resolve("single");
            Layout layout = new Layout("single", panels, 1, new int[]{612, 792});

            Map<String, String> metadata = new LinkedHashMap<>();
            metadata.put("source_format", "docx");

            var props = doc.getProperties().getCoreProperties();
            if (props.getTitle() != null && !props.getTitle().isBlank()) {
                metadata.put("title", props.getTitle());
            }
            if (props.getCreator() != null && !props.getCreator().isBlank()) {
                metadata.put("author", props.getCreator());
            }

            return new ParseResult(sb.toString(), layout, metadata, imageRegions);

        } catch (IOException e) {
            throw new RuntimeException("Failed to parse DOCX: " + sourceRef, e);
        }
    }

    private void renderParagraph(XWPFParagraph para, StringBuilder sb,
                                  List<ImageRegion> imageRegions, DocumentParserConfig config) {
        String style = para.getStyle();
        String text = para.getText().strip();

        if (text.isEmpty() && !hasImages(para)) return;

        if (style != null) {
            String lower = style.toLowerCase();
            if (lower.contains("heading1") || lower.equals("heading 1")) {
                sb.append("# ").append(text).append('\n');
                return;
            } else if (lower.contains("heading2") || lower.equals("heading 2")) {
                sb.append("## ").append(text).append('\n');
                return;
            } else if (lower.contains("heading3") || lower.equals("heading 3")) {
                sb.append("### ").append(text).append('\n');
                return;
            }
        }

        if (para.getRuns() != null) {
            for (XWPFRun run : para.getRuns()) {
                int fontSize = run.getFontSizeAsDouble() != null ? run.getFontSizeAsDouble().intValue() : 0;
                if (run.isBold() && fontSize >= 16 && !text.isEmpty()) {
                    sb.append("# ").append(text).append('\n');
                    extractImages(run, sb, imageRegions, config);
                    return;
                } else if (run.isBold() && fontSize >= 14 && !text.isEmpty()) {
                    sb.append("## ").append(text).append('\n');
                    extractImages(run, sb, imageRegions, config);
                    return;
                }
            }
        }

        if (!text.isEmpty()) {
            sb.append(text).append('\n');
        }

        if (para.getRuns() != null) {
            for (XWPFRun run : para.getRuns()) {
                extractImages(run, sb, imageRegions, config);
            }
        }
    }

    private void renderTable(XWPFTable table, StringBuilder sb) {
        List<XWPFTableRow> rows = table.getRows();
        if (rows.isEmpty()) return;

        int cols = rows.get(0).getTableCells().size();
        sb.append("#@ table: rows=").append(rows.size()).append(" cols=").append(cols).append('\n');

        for (int i = 0; i < rows.size(); i++) {
            XWPFTableRow row = rows.get(i);
            sb.append("|");
            for (XWPFTableCell cell : row.getTableCells()) {
                sb.append(" ").append(cell.getText().strip()).append(" |");
            }
            sb.append('\n');

            if (i == 0) {
                sb.append("|");
                for (int c = 0; c < row.getTableCells().size(); c++) {
                    sb.append("---|");
                }
                sb.append('\n');
            }
        }
    }

    private boolean hasImages(XWPFParagraph para) {
        if (para.getRuns() == null) return false;
        for (XWPFRun run : para.getRuns()) {
            if (!run.getEmbeddedPictures().isEmpty()) return true;
        }
        return false;
    }

    private void extractImages(XWPFRun run, StringBuilder sb,
                                List<ImageRegion> imageRegions, DocumentParserConfig config) {
        for (XWPFPicture pic : run.getEmbeddedPictures()) {
            byte[] imageBytes = pic.getPictureData().getData();
            String contentType = pic.getPictureData().getPackagePart().getContentType();
            String description = pic.getDescription();

            sb.append("#@ figure: ");
            if (description != null && !description.isBlank()) {
                sb.append("caption=\"").append(description.replace("\"", "\\\"")).append("\"");
            }
            sb.append('\n');

            if (description != null && !description.isBlank()) {
                sb.append("[").append(description).append("]\n");
            }

            String llmDescription = null;
            if (config.llmImageDescriptionEnabled() && imageDescriber != null) {
                llmDescription = imageDescriber.describe(imageBytes, contentType);
                sb.append("#@ describe: \"").append(llmDescription.replace("\"", "\\\"")).append("\"\n");
            }

            imageRegions.add(new ImageRegion(
                "main", 1,
                description != null ? description : "",
                "",
                llmDescription,
                imageBytes));
        }
    }
}

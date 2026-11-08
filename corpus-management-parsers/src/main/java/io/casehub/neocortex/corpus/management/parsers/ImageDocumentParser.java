package io.casehub.neocortex.corpus.management.parsers;

import io.casehub.neocortex.corpus.management.ImageDescriber;
import io.casehub.neocortex.corpus.management.PanelDefinition;
import org.apache.tika.exception.TikaException;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.parser.AutoDetectParser;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.sax.BodyContentHandler;
import org.xml.sax.SAXException;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class ImageDocumentParser implements DocumentParser {

    private static final Set<String> SUPPORTED = Set.of(
        "image/png", "image/jpeg", "image/tiff", "image/bmp", "image/gif");

    private final ImageDescriber imageDescriber;

    public ImageDocumentParser(ImageDescriber imageDescriber) {
        this.imageDescriber = imageDescriber;
    }

    @Override
    public Set<String> supportedMediaTypes() {
        return SUPPORTED;
    }

    @Override
    public ParseResult parse(InputStream content, String sourceRef, DocumentParserConfig config) {
        try {
            byte[] imageBytes = readAllBytes(content);

            String ocrText = "";
            if (config.ocrEnabled()) {
                ocrText = extractOcrText(imageBytes, sourceRef);
            }

            String mimeType = detectMimeType(sourceRef);
            String llmDescription = null;
            if (config.llmImageDescriptionEnabled() && imageDescriber != null) {
                llmDescription = imageDescriber.describe(imageBytes, mimeType);
            }

            var sb = new StringBuilder();
            sb.append("#@ page: 1\n");
            sb.append("#@ panel: main\n");

            if (!ocrText.isBlank()) {
                sb.append("#@ ocr: \"").append(escapeQuotes(ocrText.strip())).append("\"\n");
                sb.append(ocrText.strip()).append('\n');
            }

            if (llmDescription != null && !llmDescription.isBlank()) {
                sb.append("#@ describe: \"").append(escapeQuotes(llmDescription)).append("\"\n");
            }

            List<PanelDefinition> panels = LayoutPresets.resolve("single");
            Layout layout = new Layout("single", panels, 1, new int[]{0, 0});

            Map<String, String> metadata = new LinkedHashMap<>();
            metadata.put("source_format", "image");
            metadata.put("mime_type", mimeType);

            List<ImageRegion> imageRegions = new ArrayList<>();
            imageRegions.add(new ImageRegion(
                "main", 1, "", ocrText, llmDescription, imageBytes));

            return new ParseResult(sb.toString(), layout, metadata, imageRegions);

        } catch (IOException e) {
            throw new RuntimeException("Failed to parse image: " + sourceRef, e);
        }
    }

    private String extractOcrText(byte[] imageBytes, String sourceRef) {
        try {
            BodyContentHandler handler = new BodyContentHandler(-1);
            Metadata tikaMetadata = new Metadata();
            AutoDetectParser parser = new AutoDetectParser();
            parser.parse(new java.io.ByteArrayInputStream(imageBytes),
                handler, tikaMetadata, new ParseContext());
            return handler.toString().strip();
        } catch (IOException | SAXException | TikaException e) {
            return "";
        }
    }

    private static String detectMimeType(String sourceRef) {
        if (sourceRef == null) return "image/png";
        String lower = sourceRef.toLowerCase();
        if (lower.endsWith(".jpg") || lower.endsWith(".jpeg")) return "image/jpeg";
        if (lower.endsWith(".tiff") || lower.endsWith(".tif")) return "image/tiff";
        if (lower.endsWith(".bmp")) return "image/bmp";
        if (lower.endsWith(".gif")) return "image/gif";
        return "image/png";
    }

    private static String escapeQuotes(String s) {
        return s.replace("\"", "\\\"").replace("\n", " ");
    }

    private static byte[] readAllBytes(InputStream in) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buf = new byte[8192];
        int n;
        while ((n = in.read(buf)) != -1) {
            out.write(buf, 0, n);
        }
        return out.toByteArray();
    }
}

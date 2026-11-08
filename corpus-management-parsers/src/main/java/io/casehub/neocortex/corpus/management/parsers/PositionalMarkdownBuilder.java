package io.casehub.neocortex.corpus.management.parsers;

import io.casehub.neocortex.corpus.management.PanelDefinition;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Map;
import java.util.UUID;

public class PositionalMarkdownBuilder {

    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("yyyyMMdd");

    public String build(ParseResult parseResult, String artifactRef) {
        if (parseResult == null) throw new IllegalArgumentException("parseResult must not be null");

        Layout layout = parseResult.layout();
        Map<String, String> metadata = parseResult.extractedMetadata();

        var sb = new StringBuilder();
        sb.append("---\n");
        sb.append("id: ").append(generateId()).append('\n');
        sb.append("content_format: positional-markdown\n");

        String sourceFormat = metadata.getOrDefault("source_format", "unknown");
        sb.append("source_format: ").append(sourceFormat).append('\n');
        sb.append("source_pages: ").append(layout.pageCount()).append('\n');
        sb.append("page_size: [").append(layout.pageSize()[0]).append(", ").append(layout.pageSize()[1]).append("]\n");
        sb.append("layout: ").append(layout.name()).append('\n');

        if (!layout.panels().isEmpty() && !"single".equals(layout.name())) {
            sb.append("panels:\n");
            for (PanelDefinition panel : layout.panels()) {
                sb.append("  - id: ").append(panel.id()).append('\n');
                sb.append("    x: ").append(panel.x()).append('\n');
                sb.append("    y: ").append(panel.y()).append('\n');
                sb.append("    w: ").append(panel.w()).append('\n');
                sb.append("    h: ").append(panel.h()).append('\n');
            }
        }

        if (artifactRef != null && !artifactRef.isBlank()) {
            sb.append("artifact_ref: ").append(artifactRef).append('\n');
        }

        String title = metadata.get("title");
        if (title != null && !title.isBlank()) {
            sb.append("title: \"").append(escapeYaml(title)).append("\"\n");
        }

        sb.append("---\n");
        sb.append(parseResult.positionalMarkdown());

        return sb.toString();
    }

    private String generateId() {
        String datePart = LocalDate.now().format(DATE_FORMAT);
        String randomPart = UUID.randomUUID().toString().substring(0, 6);
        return "CE-" + datePart + "-" + randomPart;
    }

    private String escapeYaml(String value) {
        return value.replace("\"", "\\\"");
    }
}

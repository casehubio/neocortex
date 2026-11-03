package io.casehub.neocortex.corpus.management.impl;

import io.casehub.neocortex.corpus.management.InteractionContext;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.UUID;

public class CoverDocumentGenerator {

    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("yyyyMMdd");

    public String generateTranscriptCover(InteractionContext context) {
        String id = generateId();
        String title = deriveTitle(context);
        String corpusName = context.agentId() + "-interactions";

        var sb = new StringBuilder();
        sb.append("---\n");
        sb.append("id: ").append(id).append('\n');
        sb.append("title: \"").append(escapeYaml(title)).append("\"\n");
        sb.append("type: transcript\n");
        sb.append("corpus: ").append(corpusName).append('\n');
        sb.append("interaction_type: ").append(context.interactionType().label()).append('\n');
        sb.append("activity_type: ").append(context.activityType().label()).append('\n');
        sb.append("artifact_ref: ").append(context.artifactRef()).append('\n');

        if (context.domain() != null && !context.domain().isBlank()) {
            sb.append("domain: ").append(context.domain()).append('\n');
        }
        if (!context.participants().isEmpty()) {
            sb.append("participants: ").append(formatList(context.participants())).append('\n');
        }
        if (context.turnCount() > 0) {
            sb.append("turn_count: ").append(context.turnCount()).append('\n');
        }
        if (!context.relatedNodeIds().isEmpty()) {
            sb.append("related_nodes: ").append(formatList(context.relatedNodeIds())).append('\n');
        }

        sb.append("---\n");
        sb.append(context.brief()).append('\n');

        return sb.toString();
    }

    private String generateId() {
        String datePart = LocalDate.now().format(DATE_FORMAT);
        String randomPart = UUID.randomUUID().toString().substring(0, 6);
        return "CE-" + datePart + "-" + randomPart;
    }

    private String deriveTitle(InteractionContext context) {
        String brief = context.brief();
        int maxLen = 75;
        if (brief.length() <= maxLen) {
            return brief.contains(".") ? brief.substring(0, brief.indexOf('.')).strip() : brief.strip();
        }
        int end = brief.lastIndexOf(' ', maxLen);
        if (end < 20) end = maxLen;
        return brief.substring(0, end).strip();
    }

    private String formatList(List<String> items) {
        return "[" + String.join(", ", items) + "]";
    }

    private String escapeYaml(String value) {
        return value.replace("\"", "\\\"");
    }
}

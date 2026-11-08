package io.casehub.neocortex.corpus.management.parsers;

import io.casehub.neocortex.corpus.management.BlockAnnotation;
import io.casehub.neocortex.corpus.management.BlockAnnotationParser;
import io.casehub.neocortex.corpus.management.BlockParseResult;
import io.casehub.neocortex.corpus.management.StructuralChunker;
import io.casehub.neocortex.corpus.management.StructuredChunk;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class DefaultStructuralChunker implements StructuralChunker {

    private static final Set<String> SPLIT_TYPES = Set.of("page", "panel", "layout");
    private static final Pattern HEADING = Pattern.compile("^(#{1,6})\\s+(.+)$", Pattern.MULTILINE);

    private final int chunkOverlap;
    private final BlockAnnotationParser parser = new BlockAnnotationParser();

    public DefaultStructuralChunker(int chunkOverlap) {
        this.chunkOverlap = chunkOverlap;
    }

    @Override
    public List<StructuredChunk> chunk(String positionalMarkdown, int maxChunkSize) {
        if (positionalMarkdown == null || positionalMarkdown.isBlank()) return List.of();

        BlockParseResult parsed = parser.parse(positionalMarkdown);
        String cleanText = parsed.cleanText();
        List<BlockAnnotation> annotations = parsed.annotations();

        if (annotations.isEmpty()) {
            return splitBySize(cleanText, maxChunkSize, Map.of());
        }

        List<Segment> segments = buildSegments(cleanText, annotations);
        List<StructuredChunk> result = new ArrayList<>();

        for (Segment segment : segments) {
            String text = segment.text.strip();
            if (text.isEmpty()) continue;

            if (text.length() <= maxChunkSize) {
                result.add(new StructuredChunk(text, segment.metadata));
            } else {
                result.addAll(splitByHeadings(text, maxChunkSize, segment.metadata));
            }
        }

        return mergeSmallChunks(result, maxChunkSize);
    }

    private List<Segment> buildSegments(String cleanText, List<BlockAnnotation> annotations) {
        List<Segment> segments = new ArrayList<>();

        String currentPage = null;
        String currentPanel = null;
        String currentLayout = null;
        boolean hasFigure = false;
        boolean hasTable = false;

        int segmentStart = 0;

        for (int i = 0; i < annotations.size(); i++) {
            BlockAnnotation ann = annotations.get(i);

            switch (ann.type()) {
                case "page" -> {
                    if (i > 0 && segmentStart < ann.startOffset()) {
                        segments.add(makeSegment(cleanText, segmentStart, ann.startOffset(),
                            currentPage, currentPanel, hasFigure, hasTable));
                        hasFigure = false;
                        hasTable = false;
                    }
                    currentPage = ann.value();
                    segmentStart = ann.startOffset();
                }
                case "panel" -> {
                    if (segmentStart < ann.startOffset()) {
                        segments.add(makeSegment(cleanText, segmentStart, ann.startOffset(),
                            currentPage, currentPanel, hasFigure, hasTable));
                        hasFigure = false;
                        hasTable = false;
                    }
                    currentPanel = ann.value();
                    segmentStart = ann.startOffset();
                }
                case "layout" -> {
                    if (segmentStart < ann.startOffset()) {
                        segments.add(makeSegment(cleanText, segmentStart, ann.startOffset(),
                            currentPage, currentPanel, hasFigure, hasTable));
                        hasFigure = false;
                        hasTable = false;
                    }
                    currentLayout = ann.value();
                    segmentStart = ann.startOffset();
                }
                case "figure" -> hasFigure = true;
                case "table" -> hasTable = true;
            }
        }

        if (segmentStart < cleanText.length()) {
            segments.add(makeSegment(cleanText, segmentStart, cleanText.length(),
                currentPage, currentPanel, hasFigure, hasTable));
        }

        return segments;
    }

    private Segment makeSegment(String cleanText, int start, int end,
                                String page, String panel, boolean hasFigure, boolean hasTable) {
        String text = cleanText.substring(start, end);
        Map<String, String> metadata = new LinkedHashMap<>();
        if (page != null) metadata.put("page", page);
        if (panel != null) metadata.put("panel", panel);
        if (hasFigure) metadata.put("has_figure", "true");
        if (hasTable) metadata.put("has_table", "true");

        Matcher headingMatcher = HEADING.matcher(text);
        if (headingMatcher.find()) {
            metadata.put("section", headingMatcher.group(2).strip());
        }

        return new Segment(text, metadata);
    }

    private List<StructuredChunk> splitByHeadings(String text, int maxChunkSize, Map<String, String> baseMetadata) {
        List<StructuredChunk> chunks = new ArrayList<>();
        Matcher m = HEADING.matcher(text);
        List<Integer> headingPositions = new ArrayList<>();
        List<String> headingTexts = new ArrayList<>();

        while (m.find()) {
            headingPositions.add(m.start());
            headingTexts.add(m.group(2).strip());
        }

        if (headingPositions.size() <= 1) {
            return splitBySize(text, maxChunkSize, baseMetadata);
        }

        for (int i = 0; i < headingPositions.size(); i++) {
            int start = headingPositions.get(i);
            int end = (i + 1 < headingPositions.size()) ? headingPositions.get(i + 1) : text.length();
            String section = text.substring(start, end).strip();

            if (section.isEmpty()) continue;

            Map<String, String> meta = new LinkedHashMap<>(baseMetadata);
            meta.put("section", headingTexts.get(i));

            if (section.length() <= maxChunkSize) {
                chunks.add(new StructuredChunk(section, meta));
            } else {
                chunks.addAll(splitBySize(section, maxChunkSize, meta));
            }
        }

        String preHeading = text.substring(0, headingPositions.get(0)).strip();
        if (!preHeading.isEmpty()) {
            if (preHeading.length() <= maxChunkSize) {
                chunks.add(0, new StructuredChunk(preHeading, baseMetadata));
            } else {
                chunks.addAll(0, splitBySize(preHeading, maxChunkSize, baseMetadata));
            }
        }

        return chunks;
    }

    private List<StructuredChunk> splitBySize(String text, int maxChunkSize, Map<String, String> metadata) {
        if (text.length() <= maxChunkSize) {
            return List.of(new StructuredChunk(text, metadata));
        }

        List<StructuredChunk> chunks = new ArrayList<>();
        int pos = 0;
        while (pos < text.length()) {
            int end = Math.min(pos + maxChunkSize, text.length());
            if (end < text.length()) {
                int lastSpace = text.lastIndexOf(' ', end);
                if (lastSpace > pos) end = lastSpace;
            }
            chunks.add(new StructuredChunk(text.substring(pos, end).strip(), metadata));
            pos = Math.max(end - chunkOverlap, pos + 1);
        }
        return chunks;
    }

    private List<StructuredChunk> mergeSmallChunks(List<StructuredChunk> chunks, int maxChunkSize) {
        if (chunks.size() <= 1) return chunks;

        int threshold = maxChunkSize / 5;
        List<StructuredChunk> result = new ArrayList<>();

        for (int i = 0; i < chunks.size(); i++) {
            StructuredChunk current = chunks.get(i);
            if (current.text().length() < threshold && i + 1 < chunks.size()) {
                StructuredChunk next = chunks.get(i + 1);
                if (current.metadata().getOrDefault("panel", "").equals(next.metadata().getOrDefault("panel", ""))
                    && current.metadata().getOrDefault("page", "").equals(next.metadata().getOrDefault("page", ""))) {
                    String merged = current.text() + "\n" + next.text();
                    chunks.set(i + 1, new StructuredChunk(merged, next.metadata()));
                    continue;
                }
            }
            result.add(current);
        }
        return result;
    }

    private record Segment(String text, Map<String, String> metadata) {}
}

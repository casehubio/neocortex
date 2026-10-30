package io.casehub.neocortex.mindmap.intelligence;

import io.casehub.neocortex.annotation.AnnotationMethod;
import io.casehub.neocortex.annotation.AnnotationSpanResolver;
import io.casehub.neocortex.annotation.EntityAnnotation;
import io.casehub.neocortex.annotation.EntityAnnotationIndex;
import io.casehub.neocortex.annotation.MentionInput;
import io.casehub.neocortex.annotation.ResolvedSpan;
import io.casehub.neocortex.memory.CaseMemoryStore;
import io.casehub.neocortex.memory.Memory;
import io.casehub.neocortex.memory.MemoryScanRequest;
import io.casehub.platform.api.identity.PrincipalId;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Instance;
import jakarta.inject.Inject;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;

@ApplicationScoped
public class BulkReExtractionService {

    private static final Logger LOG = Logger.getLogger(BulkReExtractionService.class.getName());
    private static final int SCAN_BATCH_SIZE = 100;
    private static final String SOURCE_TYPE_MEMORY = "memory";

    private final MindMapExtractor extractor;
    private final EntityAnnotationIndex annotationIndex;
    private final CaseMemoryStore memoryStore;
    private final AnnotationSpanResolver spanResolver = new AnnotationSpanResolver();

    @Inject
    public BulkReExtractionService(MindMapExtractor extractor,
                                    Instance<EntityAnnotationIndex> annotationIndex,
                                    Instance<CaseMemoryStore> memoryStore) {
        this.extractor = extractor;
        this.annotationIndex = annotationIndex.isResolvable() ? annotationIndex.get() : null;
        this.memoryStore = memoryStore.isResolvable() ? memoryStore.get() : null;
    }

    BulkReExtractionService(MindMapExtractor extractor,
                             EntityAnnotationIndex annotationIndex,
                             CaseMemoryStore memoryStore) {
        this.extractor = extractor;
        this.annotationIndex = annotationIndex;
        this.memoryStore = memoryStore;
    }

    public ReExtractionResult reExtract(String sourceType, String tenantId, PrincipalId principalId) {
        if (annotationIndex == null) {
            return new ReExtractionResult(0, 0, List.of("EntityAnnotationIndex not available"));
        }
        if (!SOURCE_TYPE_MEMORY.equals(sourceType)) {
            return new ReExtractionResult(0, 0, List.of("Unsupported source type: " + sourceType));
        }
        if (memoryStore == null) {
            return new ReExtractionResult(0, 0, List.of("CaseMemoryStore not available"));
        }
        return reExtractMemories(tenantId, principalId);
    }

    private ReExtractionResult reExtractMemories(String tenantId, PrincipalId principalId) {
        int sourcesProcessed = 0;
        int annotationsCreated = 0;
        List<String> errors = new ArrayList<>();

        String cursor = null;
        boolean hasMore = true;

        while (hasMore) {
            var request = new MemoryScanRequest(tenantId, null, null, null, SCAN_BATCH_SIZE, cursor);
            List<Memory> batch = memoryStore.scan(request);

            for (Memory memory : batch) {
                if (memory.text() == null || memory.text().isBlank()) {
                    continue;
                }
                try {
                    annotationIndex.removeBySource(SOURCE_TYPE_MEMORY, memory.memoryId(), tenantId);
                    int created = extractAndAnnotate(memory, tenantId, principalId);
                    annotationsCreated += created;
                    sourcesProcessed++;
                } catch (Exception e) {
                    LOG.log(Level.WARNING, "Re-extraction failed for memory " + memory.memoryId(), e);
                    errors.add(memory.memoryId() + ": " + e.getMessage());
                }
            }

            hasMore = batch.size() == SCAN_BATCH_SIZE;
            if (hasMore) {
                cursor = batch.getLast().memoryId();
            }
        }

        return new ReExtractionResult(sourcesProcessed, annotationsCreated, List.copyOf(errors));
    }

    private int extractAndAnnotate(Memory memory, String tenantId, PrincipalId principalId) {
        var parsed = extractor.parse(memory.text(), tenantId, List.of());
        if (parsed == null || parsed.mentions().isEmpty()) {
            return 0;
        }

        var result = extractor.apply(parsed, tenantId, principalId);

        Map<String, String> nameToNodeId = result.entities().stream()
                .collect(Collectors.toMap(ExtractedEntity::name, ExtractedEntity::nodeId, (a, b) -> a));

        List<MentionInput> mentionInputs = parsed.mentions().stream()
                .map(m -> new MentionInput(m.entityName(), m.surfaceForm(), m.mentionIndex()))
                .toList();

        List<ResolvedSpan> spans = spanResolver.resolve(memory.text(), mentionInputs);

        Instant now = Instant.now();
        List<EntityAnnotation> annotations = new ArrayList<>();
        for (ResolvedSpan span : spans) {
            String nodeId = nameToNodeId.get(span.entityName());
            if (nodeId == null) { continue; }

            annotations.add(new EntityAnnotation(
                    UUID.randomUUID().toString(),
                    tenantId,
                    SOURCE_TYPE_MEMORY,
                    memory.memoryId(),
                    nodeId,
                    span.startOffset(),
                    span.endOffset(),
                    span.surfaceForm(),
                    null,
                    AnnotationMethod.LLM_EXTRACTION,
                    memory.principalId() != null ? memory.principalId().value() : null,
                    now
            ));
        }

        if (!annotations.isEmpty()) {
            annotationIndex.addAnnotations(annotations);
        }
        return annotations.size();
    }
}

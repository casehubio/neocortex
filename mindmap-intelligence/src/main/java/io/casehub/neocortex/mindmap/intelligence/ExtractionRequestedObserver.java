package io.casehub.neocortex.mindmap.intelligence;

import io.casehub.neocortex.annotation.AnnotationMethod;
import io.casehub.neocortex.annotation.AnnotationSpanResolver;
import io.casehub.neocortex.annotation.EntityAnnotation;
import io.casehub.neocortex.annotation.EntityAnnotationIndex;
import io.casehub.neocortex.annotation.MentionInput;
import io.casehub.neocortex.annotation.ResolvedSpan;
import io.casehub.neocortex.mindmap.MindMapStore;
import io.casehub.neocortex.mindmap.MutationContext;
import io.casehub.neocortex.mindmap.intelligence.consolidation.RetrievalAccessTracker;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.ObservesAsync;
import jakarta.enterprise.inject.Instance;
import jakarta.inject.Inject;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

@ApplicationScoped
public class ExtractionRequestedObserver {

    private static final Logger LOG = Logger.getLogger(
            ExtractionRequestedObserver.class.getName());

    private final MindMapExtractor       extractor;
    private final MindMapStore           store;
    private final RetrievalAccessTracker accessTracker;
    private final EntityAnnotationIndex  annotationIndex;
    private final AnnotationSpanResolver spanResolver = new AnnotationSpanResolver();

    @Inject
    public ExtractionRequestedObserver(MindMapExtractor extractor,
                                       MindMapStore store,
                                       Instance<RetrievalAccessTracker> accessTracker,
                                       Instance<EntityAnnotationIndex> annotationIndex) {
        this.extractor       = extractor;
        this.store           = store;
        this.accessTracker   = accessTracker.isResolvable() ? accessTracker.get() : null;
        this.annotationIndex = annotationIndex.isResolvable() ? annotationIndex.get() : null;
    }

    ExtractionRequestedObserver(MindMapExtractor extractor,
                                MindMapStore store,
                                RetrievalAccessTracker accessTracker,
                                EntityAnnotationIndex annotationIndex) {
        this.extractor       = extractor;
        this.store           = store;
        this.accessTracker   = accessTracker;
        this.annotationIndex = annotationIndex;
    }

    ExtractionRequestedObserver(MindMapExtractor extractor,
                                MindMapStore store,
                                RetrievalAccessTracker accessTracker) {
        this(extractor, store, accessTracker, (EntityAnnotationIndex) null);
    }

    public void onExtractionRequested(@ObservesAsync ExtractionRequested event) {
        MutationContext.set("extraction");
        try {
            var parsed = extractor.parse(
                    event.cleanedText(), event.tenantId(), event.recentEntityNames());

            if (parsed == null) {return;}

            var result = extractor.apply(parsed, event.tenantId(), event.principalId());

            if (accessTracker != null) {
                result.entities().stream()
                      .map(ExtractedEntity::nodeId)
                      .forEach(accessTracker::recordAccess);
            }

            List<String> createdEntityIds = result.entities().stream()
                                                  .filter(ExtractedEntity::created)
                                                  .map(ExtractedEntity::nodeId)
                                                  .toList();

            if (!createdEntityIds.isEmpty()) {
                for (String segmentId : event.segmentNodeIds()) {
                    try {
                        store.supersede(segmentId, createdEntityIds.getFirst(),
                                        "llm-extraction", event.tenantId());
                    } catch (Exception e) {
                        LOG.log(Level.FINE, "Could not supersede segment " + segmentId, e);
                    }
                }
            }

            if (annotationIndex != null && !parsed.mentions().isEmpty()) {
                writeAnnotations(parsed, result, event);
            }
        } catch (Exception e) {
            LOG.log(Level.WARNING, "Extraction failed for tenant " + event.tenantId(), e);
        } finally {
            MutationContext.clear();
        }
    }

    private void writeAnnotations(ParsedExtraction parsed, ExtractionResult result,
                                  ExtractionRequested event) {
        Map<String, String> nameToNodeId = result.entities().stream()
                                                 .collect(java.util.stream.Collectors.toMap(
                                                         ExtractedEntity::name, ExtractedEntity::nodeId,
                                                         (a, b) -> a));

        List<MentionInput> mentionInputs = parsed.mentions().stream()
                                                 .map(m -> new MentionInput(m.entityName(), m.surfaceForm(), m.mentionIndex()))
                                                 .toList();

        List<ResolvedSpan> spans = spanResolver.resolve(event.cleanedText(), mentionInputs);

        String  sourceId = event.batchId() != null ? event.batchId() : "unknown";
        Instant now      = Instant.now();

        List<EntityAnnotation> annotations = new ArrayList<>();
        for (ResolvedSpan span : spans) {
            String nodeId = nameToNodeId.get(span.entityName());
            if (nodeId == null) {continue;}

            annotations.add(new EntityAnnotation(
                    java.util.UUID.randomUUID().toString(),
                    event.tenantId(),
                    "conversation",
                    sourceId,
                    nodeId,
                    span.startOffset(),
                    span.endOffset(),
                    span.surfaceForm(),
                    null,
                    AnnotationMethod.LLM_EXTRACTION,
                    event.ownerSubjectId(),
                    now
            ));
        }

        if (!annotations.isEmpty()) {
            annotationIndex.addAnnotations(annotations);
        }
    }
}

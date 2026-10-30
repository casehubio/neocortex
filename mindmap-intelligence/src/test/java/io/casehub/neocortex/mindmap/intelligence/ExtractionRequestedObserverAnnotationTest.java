package io.casehub.neocortex.mindmap.intelligence;

import io.casehub.neocortex.annotation.EntityAnnotation;
import io.casehub.neocortex.annotation.EntityAnnotationIndex;
import io.casehub.neocortex.annotation.inmem.InMemoryEntityAnnotationIndex;
import io.casehub.neocortex.mindmap.NodeInput;
import io.casehub.neocortex.mindmap.SubgraphInput;
import io.casehub.neocortex.mindmap.SubgraphTypes;
import io.casehub.neocortex.mindmap.inmem.InMemoryMindMapStore;
import io.casehub.neocortex.mindmap.intelligence.consolidation.RetrievalAccessTracker;
import io.casehub.platform.api.identity.PrincipalId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import io.casehub.neocortex.cognitive.ConfidenceOrigin;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class ExtractionRequestedObserverAnnotationTest {

    private InMemoryMindMapStore store;
    private InMemoryEntityAnnotationIndex annotationIndex;
    private RetrievalAccessTracker tracker;

    @BeforeEach
    void setUp() {
        store = new InMemoryMindMapStore();
        annotationIndex = new InMemoryEntityAnnotationIndex();
        tracker = new RetrievalAccessTracker();
        store.createSubgraph(
            new SubgraphInput("General", SubgraphTypes.GENERAL, null), "t1");
    }

    @Test
    void writesAnnotationsAfterExtraction() {
        var extractor = stubExtractor(
            new ParsedExtraction(
                List.of(new ParsedEntity("Alice", "person", Map.of(), ConfidenceOrigin.STATED)),
                List.of(), List.of(),
                List.of(
                    new ParsedMention("Alice", "Alice", 0),
                    new ParsedMention("Alice", "She", 1))),
            new ExtractionResult(
                List.of(new ExtractedEntity("node-alice", "Alice", true, "person", Map.of())),
                List.of(), List.of(), List.of("Alice")));

        var observer = new ExtractionRequestedObserver(extractor, store, tracker, annotationIndex);

        observer.onExtractionRequested(new ExtractionRequested(
            "Alice walked in. She looked around.", "t1", List.of(), List.of(),
            null, "batch-1", "agent-1"));

        List<EntityAnnotation> annotations = annotationIndex.findByEntity("node-alice", "t1");
        assertThat(annotations).hasSize(2);
        assertThat(annotations).extracting(EntityAnnotation::mentionText)
            .containsExactlyInAnyOrder("Alice", "She");
        assertThat(annotations).allMatch(a -> a.sourceId().equals("batch-1"));
        assertThat(annotations).allMatch(a -> a.ownerSubjectId().equals("agent-1"));
        assertThat(annotations).allMatch(a -> a.method().equals("llm-extraction"));
    }

    @Test
    void noAnnotationsWhenNoMentions() {
        var extractor = stubExtractor(
            new ParsedExtraction(
                List.of(new ParsedEntity("Alice", "person", Map.of(), ConfidenceOrigin.STATED)),
                List.of(), List.of()),
            new ExtractionResult(
                List.of(new ExtractedEntity("node-alice", "Alice", true, "person", Map.of())),
                List.of(), List.of(), List.of("Alice")));

        var observer = new ExtractionRequestedObserver(extractor, store, tracker, annotationIndex);

        observer.onExtractionRequested(new ExtractionRequested(
            "Alice walked in.", "t1", List.of(), List.of(), null, "batch-2", null));

        assertThat(annotationIndex.findByEntity("node-alice", "t1")).isEmpty();
    }

    @Test
    void skipsUnresolvedEntities() {
        var extractor = stubExtractor(
            new ParsedExtraction(
                List.of(new ParsedEntity("Alice", "person", Map.of(), ConfidenceOrigin.STATED)),
                List.of(), List.of(),
                List.of(
                    new ParsedMention("Alice", "Alice", 0),
                    new ParsedMention("Bob", "Bob", 1))),
            new ExtractionResult(
                List.of(new ExtractedEntity("node-alice", "Alice", true, "person", Map.of())),
                List.of(), List.of(), List.of("Alice")));

        var observer = new ExtractionRequestedObserver(extractor, store, tracker, annotationIndex);

        observer.onExtractionRequested(new ExtractionRequested(
            "Alice met Bob.", "t1", List.of(), List.of(), null, "batch-3", null));

        List<EntityAnnotation> annotations = annotationIndex.findByEntity("node-alice", "t1");
        assertThat(annotations).hasSize(1);
        assertThat(annotations.getFirst().mentionText()).isEqualTo("Alice");
    }

    @Test
    void gracefulWithoutAnnotationIndex() {
        var extractor = stubExtractor(
            new ParsedExtraction(
                List.of(new ParsedEntity("Alice", "person", Map.of(), ConfidenceOrigin.STATED)),
                List.of(), List.of(),
                List.of(new ParsedMention("Alice", "Alice", 0))),
            new ExtractionResult(
                List.of(new ExtractedEntity("node-alice", "Alice", true, "person", Map.of())),
                List.of(), List.of(), List.of("Alice")));

        var observer = new ExtractionRequestedObserver(extractor, store, tracker);

        observer.onExtractionRequested(new ExtractionRequested(
            "Alice walked in.", "t1", List.of(), List.of(), null, "batch-4", null));
        // no exception — graceful degradation
    }

    @Test
    void annotationOffsetsMatchSourceText() {
        String text = "James gave Clara the key.";
        var extractor = stubExtractor(
            new ParsedExtraction(
                List.of(
                    new ParsedEntity("James", "person", Map.of(), ConfidenceOrigin.STATED),
                    new ParsedEntity("Clara", "person", Map.of(), ConfidenceOrigin.STATED)),
                List.of(), List.of(),
                List.of(
                    new ParsedMention("James", "James", 0),
                    new ParsedMention("Clara", "Clara", 1))),
            new ExtractionResult(
                List.of(
                    new ExtractedEntity("node-james", "James", true, "person", Map.of()),
                    new ExtractedEntity("node-clara", "Clara", true, "person", Map.of())),
                List.of(), List.of(), List.of("James", "Clara")));

        var observer = new ExtractionRequestedObserver(extractor, store, tracker, annotationIndex);

        observer.onExtractionRequested(new ExtractionRequested(
            text, "t1", List.of(), List.of(), null, "batch-5", null));

        List<EntityAnnotation> all = annotationIndex.findBySource("conversation", "batch-5", "t1");
        assertThat(all).hasSize(2);
        for (EntityAnnotation a : all) {
            assertThat(text.substring(a.startOffset(), a.endOffset()))
                .isEqualTo(a.mentionText());
        }
    }

    private MindMapExtractor stubExtractor(ParsedExtraction parsed, ExtractionResult result) {
        return new MindMapExtractor(store, null) {
            @Override
            public ParsedExtraction parse(String text, String tenantId,
                                          List<String> recentEntityNames) {
                return parsed;
            }

            @Override
            public ExtractionResult apply(ParsedExtraction p, String tenantId,
                                          PrincipalId principalId) {
                return result;
            }
        };
    }
}

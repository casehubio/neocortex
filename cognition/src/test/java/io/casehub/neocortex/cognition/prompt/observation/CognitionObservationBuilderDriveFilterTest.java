package io.casehub.neocortex.cognition.prompt.observation;

import io.casehub.neocortex.caps.ClassificationResult;
import io.casehub.neocortex.caps.SituationActivation;
import io.casehub.neocortex.caps.SituationContext;
import io.casehub.neocortex.mindmap.NodeInput;
import io.casehub.neocortex.mindmap.SubgraphInput;
import io.casehub.neocortex.mindmap.SubgraphTypes;
import io.casehub.neocortex.mindmap.inmem.InMemoryMindMapStore;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.logging.Handler;
import java.util.logging.LogRecord;
import java.util.logging.Logger;

import static org.assertj.core.api.Assertions.assertThat;

class CognitionObservationBuilderDriveFilterTest {

    private final InMemoryMindMapStore store = new InMemoryMindMapStore();
    private final String subgraph = store.createSubgraph(
            new SubgraphInput("cognitive-test", SubgraphTypes.COGNITIVE, null), "tenant");
    private final CognitionObservationBuilder builder = new CognitionObservationBuilder(
            "agent", "tenant", store, null,
            (text, metadata, context) -> new ClassificationResult(List.of()), List.of());

    @Test
    void specificActivationsDiscriminateProfilesWhileRetainingRestingAndUnprofiledDrives() {
        profile("curiosity", "novelty:1.0,uncertainty:1.0");
        profile("protection", "threat:1.0");
        profile("dominance", "competition:1.0");
        var curiosity = new DriveSnapshot("curiosity", 0.4, "Explore");
        var protection = new DriveSnapshot("protection", 0.4, "Protect");
        var dominance = new DriveSnapshot("dominance", 0.9, "Lead");
        var unprofiled = new DriveSnapshot("other", 0.2, "Other");
        var situation = SituationContext.from(List.of(new SituationActivation("novelty", 0.8)));

        assertThat(builder.appraisalFilterDrives(
                List.of(curiosity, protection, dominance, unprofiled), situation, List.of()))
                .containsExactly(curiosity, dominance, unprofiled);
        assertThat(builder.appraisalFilterDrives(
                List.of(curiosity, protection),
                SituationContext.from(List.of(new SituationActivation("threat", 0.8))), List.of()))
                .containsExactly(protection);
    }

    @Test
    void emptyClassificationReportsUnfilteredFallback() {
        var records = new ArrayList<LogRecord>();
        var logger = Logger.getLogger(CognitionObservationBuilder.class.getName());
        var handler = new Handler() {
            @Override public void publish(LogRecord record) { records.add(record); }
            @Override public void flush() {}
            @Override public void close() {}
        };
        logger.addHandler(handler);
        var drives = List.of(new DriveSnapshot("curiosity", 0.4, "Explore"));
        try {
            var situation = builder.classifySituation("An unfamiliar room", Map.of());
            assertThat(situation.isEmpty()).isTrue();
            assertThat(builder.appraisalFilterDrives(drives, situation, List.of())).isEqualTo(drives);
            assertThat(records).anySatisfy(record -> assertThat(record.getMessage())
                    .contains("agent", "empty classification", "fallback=unfiltered", "drives=1"));
        } finally {
            logger.removeHandler(handler);
        }
    }

    private void profile(String drive, String inputs) {
        store.addNode(NodeInput.of(drive, subgraph).withTraits(Set.of("DriveProfileInferred"))
                .withProperties(Map.of("agent-id", "agent", "drive-type", drive, "inferred-inputs", inputs)), "tenant");
    }
}

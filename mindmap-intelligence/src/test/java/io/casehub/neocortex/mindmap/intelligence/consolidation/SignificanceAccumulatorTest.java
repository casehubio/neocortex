package io.casehub.neocortex.mindmap.intelligence.consolidation;

import io.casehub.neocortex.memory.experience.ExperienceRecorded;
import io.casehub.neocortex.memory.experience.Observation;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class SignificanceAccumulatorTest {

    private List<String> consolidatedTenants;
    private SignificanceAccumulator accumulator;

    @BeforeEach
    void setUp() {
        consolidatedTenants = new ArrayList<>();
        accumulator = new SignificanceAccumulator(
            e -> 1.0, 3.0, consolidatedTenants::add);
    }

    private ExperienceRecorded event(String tenantId) {
        return new ExperienceRecorded(
            new Observation("agent-1", tenantId, null, "turn-1",
                Instant.now(), "something happened", null, Map.of(), "entity-1"),
            "mem-1");
    }

    @Test
    void belowThreshold_doesNotTrigger() {
        accumulator.onExperienceRecorded(event("t1"));
        accumulator.onExperienceRecorded(event("t1"));
        assertThat(consolidatedTenants).isEmpty();
    }

    @Test
    void atThreshold_triggers() {
        accumulator.onExperienceRecorded(event("t1"));
        accumulator.onExperienceRecorded(event("t1"));
        accumulator.onExperienceRecorded(event("t1"));
        assertThat(consolidatedTenants).containsExactly("t1");
    }

    @Test
    void perTenantAccumulation_independent() {
        accumulator.onExperienceRecorded(event("t1"));
        accumulator.onExperienceRecorded(event("t1"));
        accumulator.onExperienceRecorded(event("t2"));
        assertThat(consolidatedTenants).isEmpty();

        accumulator.onExperienceRecorded(event("t1"));
        assertThat(consolidatedTenants).containsExactly("t1");
    }

    @Test
    void swapAndReset_clearsCounters() {
        accumulator.onExperienceRecorded(event("t1"));
        accumulator.onExperienceRecorded(event("t1"));

        SignificanceSnapshot snapshot = accumulator.swapAndReset();
        assertThat(snapshot.perTenant()).containsEntry("t1", 2.0);

        accumulator.onExperienceRecorded(event("t1"));
        assertThat(consolidatedTenants).isEmpty();
    }

    @Test
    void swapAndReset_withTenantSet_onlyDrainsSpecifiedTenants() {
        accumulator.onExperienceRecorded(event("t1"));
        accumulator.onExperienceRecorded(event("t1"));
        accumulator.onExperienceRecorded(event("t2"));
        accumulator.onExperienceRecorded(event("t2"));

        var snapshot = accumulator.swapAndReset(java.util.Set.of("t1"));

        assertThat(snapshot.perTenant()).containsEntry("t1", 2.0);
        assertThat(snapshot.perTenant()).doesNotContainKey("t2");

        var fullSnapshot = accumulator.swapAndReset();
        assertThat(fullSnapshot.perTenant()).containsEntry("t2", 2.0);
        assertThat(fullSnapshot.perTenant()).doesNotContainKey("t1");
    }

    @Test
    void swapAndReset_withTenantSet_preservesTriggeredStateForOtherTenants() {
        accumulator.onExperienceRecorded(event("t1"));
        accumulator.onExperienceRecorded(event("t1"));
        accumulator.onExperienceRecorded(event("t2"));
        accumulator.onExperienceRecorded(event("t2"));

        accumulator.swapAndReset(java.util.Set.of("t1"));

        accumulator.onExperienceRecorded(event("t2"));
        assertThat(consolidatedTenants).containsExactly("t2");
    }


    @Test
    void customExtractor_affectsAccumulation() {
        var weighted = new SignificanceAccumulator(
            e -> 5.0, 10.0, consolidatedTenants::add);
        weighted.onExperienceRecorded(event("t1"));
        assertThat(consolidatedTenants).isEmpty();
        weighted.onExperienceRecorded(event("t1"));
        assertThat(consolidatedTenants).containsExactly("t1");
    }

    @Test
    void eventsDuringReset_countTowardNextCycle() {
        accumulator.onExperienceRecorded(event("t1"));
        accumulator.onExperienceRecorded(event("t1"));
        accumulator.swapAndReset();

        accumulator.onExperienceRecorded(event("t1"));
        accumulator.onExperienceRecorded(event("t1"));
        assertThat(consolidatedTenants).isEmpty();

        accumulator.onExperienceRecorded(event("t1"));
        assertThat(consolidatedTenants).containsExactly("t1");
    }
}

package io.casehub.neocortex.mindmap.intelligence.consolidation;

import io.casehub.neocortex.memory.MemoryDomain;
import io.casehub.neocortex.memory.MemoryInput;
import io.casehub.neocortex.memory.Subject;
import io.casehub.neocortex.memory.inmem.InMemoryMemoryStore;
import io.casehub.neocortex.mindmap.MindMapNode;
import io.casehub.neocortex.mindmap.MindMapQuery;
import io.casehub.neocortex.mindmap.NodeInput;
import io.casehub.neocortex.mindmap.SubgraphTypes;
import io.casehub.neocortex.mindmap.inmem.InMemoryMindMapStore;
import io.casehub.neocortex.mindmap.runtime.IdleTracker;
import io.casehub.platform.api.identity.CurrentPrincipal;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class ConsolidationManifestTest {

    private static final String TENANT = "t1";
    private static final CurrentPrincipal PRINCIPAL = new CurrentPrincipal() {
        @Override public String actorId() { return "test"; }
        @Override public Set<String> groups() { return Set.of(); }
        @Override public String tenancyId() { return TENANT; }
        @Override public boolean isCrossTenantAdmin() { return true; }
    };

    private InMemoryMindMapStore mindMapStore;
    private InMemoryMemoryStore memoryStore;

    @BeforeEach
    void setUp() {
        mindMapStore = new InMemoryMindMapStore();
        memoryStore = new InMemoryMemoryStore(PRINCIPAL);
        memoryStore.store(MemoryInput.of(
            Subject.of("test", "seed"), new MemoryDomain("general"), TENANT, "seed"));
    }

    @Test
    void capabilityDefaultsToEmpty() {
        ConsolidationPhase phase = new ConsolidationPhase() {
            @Override public String name() { return "no-cap"; }
            @Override public void run(String t, List<String> sp) {}
        };
        var cap = phase.capability();
        assertThat(cap.requiredNodeKinds()).isEmpty();
        assertThat(cap.producedTraits()).isEmpty();
    }

    @Test
    void manifestAggregatesPhaseCapabilities() {
        var phase1 = phaseWithCapability("p1",
            Set.of("drive-intensity"), Set.of("DriveProfileInferred"));
        var phase2 = phaseWithCapability("p2",
            Set.of("personality-facet", "formative-experience"), Set.of("DriveDescriptionGenerated"));

        var scheduler = new ConsolidationScheduler(
            List.of(phase1, phase2), new IdleTracker(), memoryStore, null);
        var manifest = scheduler.manifest();

        assertThat(manifest.phases()).hasSize(2);
        assertThat(manifest.phases().get(0).name()).isEqualTo("p1");
        assertThat(manifest.phases().get(0).producedTraits()).containsExactly("DriveProfileInferred");
        assertThat(manifest.phases().get(1).name()).isEqualTo("p2");
        assertThat(manifest.phases().get(1).requiredNodeKinds()).containsExactlyInAnyOrder("personality-facet", "formative-experience");
    }

    @Test
    void manifestPipelineHashChangesWhenPhasesChange() {
        var phase1 = phaseWithCapability("p1", Set.of(), Set.of("TraitA"));
        var phase2 = phaseWithCapability("p2", Set.of(), Set.of("TraitB"));
        var phase3 = phaseWithCapability("p3", Set.of(), Set.of("TraitC"));

        var scheduler12 = new ConsolidationScheduler(
            List.of(phase1, phase2), new IdleTracker(), memoryStore, null);
        var scheduler13 = new ConsolidationScheduler(
            List.of(phase1, phase3), new IdleTracker(), memoryStore, null);

        assertThat(scheduler12.manifest().pipelineHash())
            .isNotEqualTo(scheduler13.manifest().pipelineHash());
    }

    @Test
    void manifestPipelineHashStableForSamePhases() {
        var phase1 = phaseWithCapability("p1", Set.of(), Set.of("TraitA"));
        var phase2 = phaseWithCapability("p2", Set.of(), Set.of("TraitB"));

        var schedulerA = new ConsolidationScheduler(
            List.of(phase1, phase2), new IdleTracker(), memoryStore, null);
        var schedulerB = new ConsolidationScheduler(
            List.of(phase1, phase2), new IdleTracker(), memoryStore, null);

        assertThat(schedulerA.manifest().pipelineHash())
            .isEqualTo(schedulerB.manifest().pipelineHash());
    }

    @Test
    void validateReportsMissingCapabilitiesWhenStoreEmpty() {
        var phase = phaseWithCapability("p1",
            Set.of(), Set.of("DriveDescriptionGenerated"));

        var scheduler = new ConsolidationScheduler(
            List.of(phase), new IdleTracker(), memoryStore, null);

        var missing = scheduler.validateCapabilities(TENANT, mindMapStore);
        assertThat(missing).hasSize(1);
        assertThat(missing.getFirst().phaseName()).isEqualTo("p1");
        assertThat(missing.getFirst().missingTrait()).isEqualTo("DriveDescriptionGenerated");
    }

    @Test
    void validateReportsNoMissingWhenTraitsPresent() {
        mindMapStore.addNode(NodeInput.of("desc-node", SubgraphTypes.COGNITIVE)
            .withTraits(Set.of("DriveDescriptionGenerated")), TENANT);

        var phase = phaseWithCapability("p1",
            Set.of(), Set.of("DriveDescriptionGenerated"));

        var scheduler = new ConsolidationScheduler(
            List.of(phase), new IdleTracker(), memoryStore, null);

        var missing = scheduler.validateCapabilities(TENANT, mindMapStore);
        assertThat(missing).isEmpty();
    }

    @Test
    void validateReportsOnlyMissingTraitsNotPresent() {
        mindMapStore.addNode(NodeInput.of("profile-node", SubgraphTypes.COGNITIVE)
            .withTraits(Set.of("DriveProfileInferred")), TENANT);

        var p1 = phaseWithCapability("derive",
            Set.of(), Set.of("DriveProfileInferred"));
        var p2 = phaseWithCapability("describe",
            Set.of(), Set.of("DriveDescriptionGenerated"));

        var scheduler = new ConsolidationScheduler(
            List.of(p1, p2), new IdleTracker(), memoryStore, null);

        var missing = scheduler.validateCapabilities(TENANT, mindMapStore);
        assertThat(missing).hasSize(1);
        assertThat(missing.getFirst().phaseName()).isEqualTo("describe");
        assertThat(missing.getFirst().missingTrait()).isEqualTo("DriveDescriptionGenerated");
    }

    @Test
    void phasesWithNoProducedTraitsNeverReportMissing() {
        var phase = phaseWithCapability("passive", Set.of("input"), Set.of());
        var scheduler = new ConsolidationScheduler(
            List.of(phase), new IdleTracker(), memoryStore, null);

        var missing = scheduler.validateCapabilities(TENANT, mindMapStore);
        assertThat(missing).isEmpty();
    }

    private ConsolidationPhase phaseWithCapability(String name,
            Set<String> required, Set<String> produced) {
        return new ConsolidationPhase() {
            @Override public String name() { return name; }
            @Override public void run(String t, List<String> sp) {}
            @Override public ConsolidationCapability capability() {
                return new ConsolidationCapability(required, produced);
            }
        };
    }
}

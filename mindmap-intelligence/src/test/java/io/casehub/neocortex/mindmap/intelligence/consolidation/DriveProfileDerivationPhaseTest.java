package io.casehub.neocortex.mindmap.intelligence.consolidation;

import io.casehub.neocortex.caps.CapsConnection;
import io.casehub.neocortex.caps.CapsNode;
import io.casehub.neocortex.caps.CapsTopology;
import io.casehub.neocortex.caps.NodeRange;
import io.casehub.neocortex.caps.NodeType;
import io.casehub.neocortex.caps.ClassificationResult;
import io.casehub.neocortex.caps.SituationActivation;
import io.casehub.neocortex.caps.SituationClassifier;
import io.casehub.neocortex.caps.WeightProvenance;
import io.casehub.neocortex.mindmap.MindMapNode;
import io.casehub.neocortex.mindmap.MindMapQuery;
import io.casehub.neocortex.mindmap.NodeInput;
import io.casehub.neocortex.mindmap.SubgraphInput;
import io.casehub.neocortex.mindmap.SubgraphTypes;
import io.casehub.neocortex.mindmap.inmem.InMemoryMindMapStore;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class DriveProfileDerivationPhaseTest {

    private InMemoryMindMapStore store;
    private DriveProfileDerivationPhase phase;
    private String cognitiveSubgraphId;
    private static final String TENANT = "t1";
    private static final String AGENT = "peter-perfect";

    @BeforeEach
    void setUp() {
        store = new InMemoryMindMapStore();

        SituationClassifier classifier = (desc, meta, ctx) -> {
            if (desc.contains("knelt") || desc.contains("stranger")) {
                return new ClassificationResult(List.of(
                    new SituationActivation("acceptance", 0.7),
                    new SituationActivation("sharing", 0.5)));
            }
            if (desc.contains("practised") || desc.contains("grade")) {
                return new ClassificationResult(List.of(
                    new SituationActivation("mastery", 0.8),
                    new SituationActivation("competence_recognition", 0.6)));
            }
            if (desc.contains("struggled") || desc.contains("helped")) {
                return new ClassificationResult(List.of(
                    new SituationActivation("acceptance", 0.6),
                    new SituationActivation("sharing", 0.7)));
            }
            return new ClassificationResult(List.of());
        };

        var topology = buildMinimalTopology();
        phase = new DriveProfileDerivationPhase(store, classifier, topology);

        cognitiveSubgraphId = store.createSubgraph(
            new SubgraphInput("cognitive-" + AGENT, SubgraphTypes.COGNITIVE, null), TENANT);
    }

    @Test
    void phaseNameIsDriveProfileDerivation() {
        assertThat(phase.name()).isEqualTo("drive-profile-derivation");
    }

    @Test
    void skipsWhenNoDriveNodes() {
        phase.run(TENANT, List.of());
        var nodes = findDriveProfileNodes();
        assertThat(nodes).isEmpty();
    }

    @Test
    void derivesProfileFromFormationMemories() {
        seedDriveNode("gallantry", 0.9);
        seedFormationMemory("Your father knelt to help the stranger", "gallantry", "other_safety");

        phase.run(TENANT, List.of());

        var profiles = findDriveProfileNodes();
        assertThat(profiles).hasSize(1);
        assertThat(profiles.getFirst().property("drive-type")).hasValue("gallantry");
        assertThat(profiles.getFirst().property("inferred-inputs")).isPresent();
        String inputs = profiles.getFirst().property("inferred-inputs").orElse("");
        assertThat(inputs).contains("acceptance");
    }

    @Test
    void skipsPinnedDrivesButDerivesOthers() {
        seedDriveNode("gallantry", 0.9);
        seedDriveNode("proving-worth", 0.8);
        seedFormationMemory("Your father knelt to help the stranger", "gallantry", "other_safety");
        seedFormationMemory("She practised every grade until it was perfect", "proving-worth", "achievement");

        DriveProfileDerivationPhase.storePinnedProfile(store, TENANT, cognitiveSubgraphId,
                                                       AGENT, "gallantry", Map.of("acceptance", 0.85, "sharing", 0.70));

        phase.run(TENANT, List.of());

        var inferred = findDriveProfileNodes();
        assertThat(inferred).hasSize(1);
        assertThat(inferred.getFirst().property("drive-type")).hasValue("proving-worth");

        var pinned = store.search(MindMapQuery.of(TENANT, 200).withType(SubgraphTypes.COGNITIVE))
                          .stream()
                          .filter(n -> n.traits().contains("DriveProfilePinned"))
                          .toList();
        assertThat(pinned).hasSize(1);
        assertThat(pinned.getFirst().property("drive-type")).hasValue("gallantry");
        assertThat(pinned.getFirst().property("inferred-inputs")).isPresent();
        assertThat(pinned.getFirst().property("inferred-inputs").orElse("")).contains("acceptance:0.850");
    }


    @Test
    void detectsCompetitionFromSharedInputNodes() {
        seedDriveNode("gallantry", 0.9);
        seedDriveNode("social-harmony", 0.8);
        seedFormationMemory("Your father knelt to help the stranger", "gallantry", "other_safety");
        seedFormationMemory("She struggled and you helped her", "social-harmony", "other_safety");

        phase.run(TENANT, List.of());

        var competitions = findCompetitionNodes();
        assertThat(competitions).hasSize(1);
        assertThat(competitions.getFirst().property("shared-mediating")).isPresent();
    }

    @Test
    void competitionGeneratedEvenWhenDrivesActivateDifferentNodes() {
        seedDriveNode("gallantry", 0.9);
        seedDriveNode("proving-worth", 0.7);
        seedFormationMemory("Your father knelt to help the stranger", "gallantry", "other_safety");
        seedFormationMemory("You practised until you earned the grade", "proving-worth", "self_efficacy");

        phase.run(TENANT, List.of());

        var competitions = findCompetitionNodes();
        assertThat(competitions).hasSize(1);
    }

    @Test
    void differentiationTextReferencesFormationMemories() {
        seedDriveNode("gallantry", 0.9);
        seedDriveNode("social-harmony", 0.8);
        seedFormationMemory("Your father knelt to help the stranger", "gallantry", "other_safety");
        seedFormationMemory("She struggled and you helped her", "social-harmony", "other_safety");

        phase.run(TENANT, List.of());

        var competitions = findCompetitionNodes();
        assertThat(competitions).hasSize(1);
        String diffText = competitions.getFirst().property("differentiation-text").orElse("");
        assertThat(diffText).isNotBlank();
        assertThat(diffText).contains("How they differ");
        assertThat(diffText).contains("not the same");
    }

    @Test
    void multipleMemoriesPerDriveAggregateActivations() {
        seedDriveNode("gallantry", 0.9);
        seedFormationMemory("Your father knelt to help the stranger", "gallantry", "other_safety");
        seedFormationMemory("She struggled and you helped her up", "gallantry", "other_safety");

        phase.run(TENANT, List.of());

        var profiles = findDriveProfileNodes();
        assertThat(profiles).hasSize(1);
        String inputs = profiles.getFirst().property("inferred-inputs").orElse("");
        assertThat(inputs).contains("acceptance");
        assertThat(inputs).contains("sharing");
    }


    @Test
    void producesProfilesWithoutClassifierOrTopology() {
        var phaseNoDeps = new DriveProfileDerivationPhase(store, (SituationClassifier) null, (CapsTopology) null);

        seedDriveNode("gallantry", 0.9);
        seedFormationMemory("Your father knelt to help the stranger", "gallantry", "other_safety");

        phaseNoDeps.run(TENANT, List.of());

        var profiles = findDriveProfileNodes();
        assertThat(profiles).hasSize(1);
        assertThat(profiles.getFirst().property("drive-type")).hasValue("gallantry");
        assertThat(profiles.getFirst().property("source-memory-count")).hasValue("1");
    }

    @Test
    void detectsCompetitionViaSharedCatalogueWithoutTopology() {
        var phaseNoDeps = new DriveProfileDerivationPhase(store, (SituationClassifier) null, (CapsTopology) null);

        seedDriveNode("gallantry", 0.9);
        seedDriveNode("proving-worth", 0.7);
        seedFormationMemory("Your father knelt to help the stranger", "gallantry", "other_safety");
        seedFormationMemory("She struggled and you helped her up", "proving-worth", "other_safety");

        phaseNoDeps.run(TENANT, List.of());

        var competitions = findCompetitionNodes();
        assertThat(competitions).hasSize(1);
        assertThat(competitions.getFirst().property("shared-mediating")).isPresent();
        String shared = competitions.getFirst().property("shared-mediating").orElse("");
        assertThat(shared).contains("other_safety");
        assertThat(competitions.getFirst().property("differentiation-text")).isPresent();
    }


    @Test
    void replacesExistingProfileOnRerun() {
        seedDriveNode("gallantry", 0.9);
        seedFormationMemory("Your father knelt to help the stranger", "gallantry", "other_safety");

        phase.run(TENANT, List.of());
        assertThat(findDriveProfileNodes()).hasSize(1);

        phase.run(TENANT, List.of());
        assertThat(findDriveProfileNodes()).hasSize(1);
    }

    // --- helpers ---

    private void seedDriveNode(String driveType, double intensity) {
        store.addNode(
            NodeInput.of(driveType, cognitiveSubgraphId)
                .withProperties(Map.of(
                    "cognitiveKind", "drive-intensity",
                    "agent-id", AGENT,
                    "drive-type", driveType,
                    "intensity", String.valueOf(intensity),
                    "description", "Drive: " + driveType)),
            TENANT);
    }

    private void seedFormationMemory(String episodeText, String driveType, String catalogue) {
        store.addNode(
            NodeInput.of(episodeText, cognitiveSubgraphId)
                .withProperties(Map.of(
                    "source-memory-id", "mem-" + episodeText.hashCode(),
                    "agent-id", AGENT,
                    "cognitiveKind", "formative-experience",
                    "situation-types", "formation,childhood," + driveType,
                    "catalogue-entry-id", catalogue,
                    "caps-processed", "true")),
            TENANT);
    }

    private List<MindMapNode> findDriveProfileNodes() {
        return store.search(MindMapQuery.of(TENANT, 200).withType(SubgraphTypes.COGNITIVE))
            .stream()
            .filter(n -> n.traits().contains("DriveProfileInferred"))
            .toList();
    }

    private List<MindMapNode> findCompetitionNodes() {
        return store.search(MindMapQuery.of(TENANT, 200).withType(SubgraphTypes.COGNITIVE))
            .stream()
            .filter(n -> n.traits().contains("DriveCompetitionInferred"))
            .toList();
    }

    private CapsTopology buildMinimalTopology() {
        var nodes = Map.of(
            "acceptance", new CapsNode("acceptance", NodeType.INPUT, NodeRange.UNIPOLAR, "social", List.of(), Map.of("welcomed", 0.8)),
            "sharing", new CapsNode("sharing", NodeType.INPUT, NodeRange.UNIPOLAR, "resource", List.of(), Map.of("helped", 0.5)),
            "mastery", new CapsNode("mastery", NodeType.INPUT, NodeRange.UNIPOLAR, "achievement", List.of(), Map.of("mastered", 0.9)),
            "competence_recognition", new CapsNode("competence_recognition", NodeType.INPUT, NodeRange.UNIPOLAR, "achievement", List.of(), Map.of("praised", 0.8)),
            "other_safety", new CapsNode("other_safety", NodeType.MEDIATING, NodeRange.BIPOLAR, "other_model", List.of(), Map.of()),
            "self_efficacy", new CapsNode("self_efficacy", NodeType.MEDIATING, NodeRange.BIPOLAR, "self_model", List.of(), Map.of()),
            "self_worth", new CapsNode("self_worth", NodeType.MEDIATING, NodeRange.BIPOLAR, "self_model", List.of(), Map.of()),
            "approach", new CapsNode("approach", NodeType.OUTPUT, NodeRange.UNIPOLAR, "approach_avoidance", List.of(), Map.of())
        );

        var connections = List.of(
            new CapsConnection("c1", "acceptance", "other_safety", 0.3, WeightProvenance.CONSENSUS, "test", List.of()),
            new CapsConnection("c2", "sharing", "other_safety", 0.25, WeightProvenance.CONSENSUS, "test", List.of()),
            new CapsConnection("c3", "mastery", "self_efficacy", 0.3, WeightProvenance.CONSENSUS, "test", List.of()),
            new CapsConnection("c4", "mastery", "self_worth", 0.25, WeightProvenance.CONSENSUS, "test", List.of()),
            new CapsConnection("c5", "competence_recognition", "self_worth", 0.3, WeightProvenance.CONSENSUS, "test", List.of()),
            new CapsConnection("c6", "competence_recognition", "self_efficacy", 0.35, WeightProvenance.CONSENSUS, "test", List.of())
        );

        return new CapsTopology(1, nodes, connections, Map.of(), List.of(), null);
    }
}

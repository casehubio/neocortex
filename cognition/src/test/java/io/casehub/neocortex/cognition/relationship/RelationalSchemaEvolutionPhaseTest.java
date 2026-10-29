package io.casehub.neocortex.cognition.relationship;

import io.casehub.neocortex.cognitive.index.AttachmentProfile;
import io.casehub.neocortex.cognitive.index.CognitiveDefaults;
import io.casehub.neocortex.cognitive.index.CognitiveDefaultsRegistry;
import io.casehub.neocortex.cognitive.index.ConflictInterpretation;
import io.casehub.neocortex.cognitive.index.PersonalityFacet;
import io.casehub.neocortex.cognitive.index.PersonalityProfile;
import io.casehub.neocortex.cognitive.index.RelationalSchema;
import io.casehub.neocortex.cognitive.index.SocialCognitionDefaults;
import io.casehub.neocortex.memory.MemoryInput;
import io.casehub.neocortex.memory.Subject;
import io.casehub.neocortex.memory.inmem.InMemoryMemoryStore;
import io.casehub.neocortex.memory.relationship.RelationshipEvents;
import io.casehub.neocortex.mindmap.MindMapNode;
import io.casehub.neocortex.mindmap.MindMapStore;
import io.casehub.neocortex.mindmap.NodeInput;
import io.casehub.neocortex.mindmap.NodeUpdate;
import io.casehub.neocortex.mindmap.OverlayRef;
import io.casehub.neocortex.mindmap.SubgraphInput;
import io.casehub.neocortex.mindmap.SubgraphTypes;
import io.casehub.neocortex.mindmap.inmem.InMemoryMindMapStore;
import io.casehub.platform.api.identity.CurrentPrincipal;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class RelationalSchemaEvolutionPhaseTest {

    private static final String TENANT = "t1";

    private InMemoryMindMapStore store;
    private InMemoryMemoryStore memoryStore;
    private RelationalSchemaEvolutionPhase phase;
    private String sgId;

    @BeforeEach
    void setUp() {
        store = new InMemoryMindMapStore();
        memoryStore = createMemoryStore();
        sgId = store.createSubgraph(new SubgraphInput("person", SubgraphTypes.PERSON, null), TENANT);
        phase = createPhase(CognitiveDefaults.empty("default")
            .withSocialCognition(SocialCognitionDefaults.defaults()));
    }

    private static InMemoryMemoryStore createMemoryStore() {
        return new InMemoryMemoryStore(new CurrentPrincipal() {
            @Override public String actorId() { return "system"; }
            @Override public Set<String> groups() { return Set.of(); }
            @Override public String tenancyId() { return TENANT; }
            @Override public boolean isCrossTenantAdmin() { return true; }
        });
    }

    private RelationalSchemaEvolutionPhase createPhase(CognitiveDefaults... profiles) {
        return new RelationalSchemaEvolutionPhase(
            store, memoryStore,
            CognitiveDefaultsRegistry.forTesting(profiles),
            "person");
    }

    private String setupOverlayPair(String observerId, String targetId) {
        var sharedId = store.addNode(NodeInput.of(targetId, sgId)
            .withProperties(Map.of("agentId", targetId)), TENANT);
        return store.addNode(NodeInput.of("overlay-" + observerId + "-" + targetId, sgId)
            .withTraits(Set.of("overlay"))
            .withProperties(Map.of(OverlayRef.AGENT_ID, observerId))
            .withRefs(Set.of(OverlayRef.of(sharedId))), TENANT);
    }

    private void setupOverlayWithSchema(String observerId, String targetId,
                                         double trust, double intimacy, String role) {
        var overlayId = setupOverlayPair(observerId, targetId);
        var schema = new RelationalSchema(targetId, role, trust, intimacy, 0.0, null);
        store.updateNode(overlayId,
            NodeUpdate.empty().withPropertiesToSet(schema.toProperties()), TENANT);
    }

    private void resetOverlay(String observerId, String targetId,
                               double trust, double intimacy, String role) {
        var overlay = findOverlay(observerId);
        var schema = new RelationalSchema(targetId, role, trust, intimacy, 0.0, null);
        store.updateNode(overlay.id(),
            NodeUpdate.empty().withPropertiesToSet(schema.toProperties()), TENANT);
    }

    private void storePositiveMemories(String observer, String target, int count) {
        for (int i = 0; i < count; i++) {
            memoryStore.store(
                MemoryInput.of(Subject.of("agent", observer), RelationshipEvents.DOMAIN, TENANT,
                    "positive interaction " + i)
                .withPad(0.5, null, null)
                .withAttribute("other-agent", target));
        }
    }

    private void storeNegativeMemories(String observer, String target, int count) {
        for (int i = 0; i < count; i++) {
            memoryStore.store(
                MemoryInput.of(Subject.of("agent", observer), RelationshipEvents.DOMAIN, TENANT,
                    "negative interaction " + i)
                .withPad(-0.5, null, null)
                .withAttribute("other-agent", target));
        }
    }

    private MindMapNode findOverlay(String observerId) {
        return store.nodesIn(sgId, TENANT).stream()
            .filter(n -> n.traits().contains("overlay"))
            .filter(n -> observerId.equals(n.property(OverlayRef.AGENT_ID).orElse(null)))
            .findFirst()
            .orElseThrow(() -> new AssertionError("No overlay found for " + observerId));
    }

    private RelationalSchema readSchema(String observerId) {
        var overlay = findOverlay(observerId);
        return RelationalSchema.fromProperties(overlay.properties());
    }

    @Test
    void seedsInitialSchemaWhenNoRelProperties() {
        setupOverlayPair("alice", "bob");
        storePositiveMemories("alice", "bob", 3);

        phase.run(TENANT, List.of());

        var schema = readSchema("alice");
        assertThat(schema).isNotNull();
        assertThat(schema.targetId()).isEqualTo("bob");
        assertThat(schema.trust()).isGreaterThan(0.0);
        assertThat(schema.intimacy()).isGreaterThan(0.0);
    }
@Test
void callousnessDampensPositiveTrustGain() {
    setupOverlayWithSchema("alice", "bob", 0.0, 0.0, "stranger");
    storePositiveMemories("alice", "bob", 5);

    var callous = CognitiveDefaults.empty("alice")
        .withSocialCognition(SocialCognitionDefaults.defaults())
        .withPersonalityProfile(new PersonalityProfile(null, null, null, null,
            List.of(new PersonalityFacet("Callousness", 0.8, "high callousness"))));
    phase = createPhase(callous);
    phase.run(TENANT, List.of());
    var schemaCallous = readSchema("alice");

    resetOverlay("alice", "bob", 0.0, 0.0, "stranger");
    var noCallous = CognitiveDefaults.empty("alice")
        .withSocialCognition(SocialCognitionDefaults.defaults());
    phase = createPhase(noCallous);
    phase.run(TENANT, List.of());
    var schemaNormal = readSchema("alice");

    assertThat(schemaCallous.trust()).isGreaterThan(0.0);
    assertThat(schemaCallous.trust()).isLessThan(schemaNormal.trust());
}
@Test
void disengageAmplifiesNegativeTrustLoss() {
    setupOverlayWithSchema("alice", "bob", 0.3, 0.0, "stranger");
    storeNegativeMemories("alice", "bob", 5);

    var disengage = CognitiveDefaults.empty("alice")
        .withSocialCognition(new SocialCognitionDefaults(0.5, ConflictInterpretation.DISENGAGE));
    phase = createPhase(disengage);
    phase.run(TENANT, List.of());
    var schemaDisengage = readSchema("alice");

    resetOverlay("alice", "bob", 0.3, 0.0, "stranger");
    var repair = CognitiveDefaults.empty("alice")
        .withSocialCognition(new SocialCognitionDefaults(0.5, ConflictInterpretation.REPAIR));
    phase = createPhase(repair);
    phase.run(TENANT, List.of());
    var schemaRepair = readSchema("alice");

    assertThat(schemaDisengage.trust()).isLessThan(schemaRepair.trust());
}
@Test
void avoidanceCapsMaxTrust() {
    setupOverlayWithSchema("alice", "bob", 0.0, 0.0, "stranger");
    storePositiveMemories("alice", "bob", 50);

    var avoidant = CognitiveDefaults.empty("alice")
        .withSocialCognition(SocialCognitionDefaults.defaults())
        .withPersonalityProfile(new PersonalityProfile(null, null, null,
            new AttachmentProfile("dismissive-avoidant", 0.1, 0.8), List.of()));
    phase = createPhase(avoidant);
    phase.run(TENANT, List.of());

    var schema = readSchema("alice");
    assertThat(schema.trust()).isLessThanOrEqualTo(0.2);
}
@Test
void anxietyAmplifiesIntimacyGain() {
    setupOverlayWithSchema("alice", "bob", 0.0, 0.0, "stranger");
    storePositiveMemories("alice", "bob", 5);

    var anxious = CognitiveDefaults.empty("alice")
        .withSocialCognition(SocialCognitionDefaults.defaults())
        .withPersonalityProfile(new PersonalityProfile(null, null, null,
            new AttachmentProfile("anxious", 0.9, 0.1), List.of()));
    phase = createPhase(anxious);
    phase.run(TENANT, List.of());
    var schemaAnxious = readSchema("alice");

    resetOverlay("alice", "bob", 0.0, 0.0, "stranger");
    var secure = CognitiveDefaults.empty("alice")
        .withSocialCognition(SocialCognitionDefaults.defaults())
        .withPersonalityProfile(new PersonalityProfile(null, null, null,
            new AttachmentProfile("secure", 0.1, 0.1), List.of()));
    phase = createPhase(secure);
    phase.run(TENANT, List.of());
    var schemaSecure = readSchema("alice");

    assertThat(schemaAnxious.intimacy()).isGreaterThan(schemaSecure.intimacy());
}

@Test
void noMemoriesNoUpdate() {
    setupOverlayWithSchema("alice", "bob", 0.3, 0.2, "ally");
    phase.run(TENANT, List.of());

    var schema = readSchema("alice");
    assertThat(schema.trust()).isEqualTo(0.3);
    assertThat(schema.intimacy()).isEqualTo(0.2);
    assertThat(schema.role()).isEqualTo("ally");
}

@Test
void negativeValenceDoesNotIncreaseIntimacy() {
    setupOverlayWithSchema("alice", "bob", 0.0, 0.0, "stranger");
    storeNegativeMemories("alice", "bob", 5);
    phase.run(TENANT, List.of());

    var schema = readSchema("alice");
    assertThat(schema.intimacy()).isEqualTo(0.0);
}
@Test
void strangerBecomesAllyAtHighTrust() {
    setupOverlayWithSchema("alice", "bob", 0.0, 0.0, "stranger");
    storePositiveMemories("alice", "bob", 20);
    phase.run(TENANT, List.of());

    var schema = readSchema("alice");
    assertThat(schema.trust()).isGreaterThanOrEqualTo(0.5);
    assertThat(schema.role()).isEqualTo("ally");
}
@Test
void manipulativeStrangerBecomesToolNotAlly() {
    setupOverlayWithSchema("alice", "bob", 0.0, 0.0, "stranger");
    storePositiveMemories("alice", "bob", 20);

    var manipulative = CognitiveDefaults.empty("alice")
        .withSocialCognition(SocialCognitionDefaults.defaults())
        .withPersonalityProfile(new PersonalityProfile(null, null, null, null,
            List.of(new PersonalityFacet("Manipulativeness", 0.8, "high manipulativeness"))));
    phase = createPhase(manipulative);
    phase.run(TENANT, List.of());

    var schema = readSchema("alice");
    assertThat(schema.role()).isEqualTo("tool");
}

@Test
void strangerBecomesThreatAtNegativeTrust() {
    setupOverlayWithSchema("alice", "bob", 0.0, 0.0, "stranger");
    storeNegativeMemories("alice", "bob", 20);
    phase.run(TENANT, List.of());

    var schema = readSchema("alice");
    assertThat(schema.trust()).isLessThanOrEqualTo(-0.3);
    assertThat(schema.role()).isEqualTo("threat");
}

@Test
void preyBecomesToolNotAllyForManipulativeAgent() {
    setupOverlayWithSchema("alice", "bob", 0.0, 0.0, "prey");
    storePositiveMemories("alice", "bob", 20);

    var manipulative = CognitiveDefaults.empty("alice")
        .withSocialCognition(SocialCognitionDefaults.defaults())
        .withPersonalityProfile(new PersonalityProfile(null, null, null, null,
            List.of(new PersonalityFacet("Manipulativeness", 0.6, "moderate manipulativeness"))));
    phase = createPhase(manipulative);
    phase.run(TENANT, List.of());

    var schema = readSchema("alice");
    assertThat(schema.role()).isEqualTo("tool");
}

@Test
void midRangeTrustNoRoleTransition() {
    setupOverlayWithSchema("alice", "bob", 0.3, 0.0, "stranger");
    // Store 1 positive and 1 negative — keeps trust in mid-range
    storePositiveMemories("alice", "bob", 1);
    storeNegativeMemories("alice", "bob", 1);
    phase.run(TENANT, List.of());

    var schema = readSchema("alice");
    assertThat(schema.trust()).isGreaterThan(-0.3).isLessThan(0.5);
    assertThat(schema.role()).isEqualTo("stranger");
}

@Test
void obstacleRecoversToStrangerAtHighTrust() {
    setupOverlayWithSchema("alice", "bob", 0.0, 0.0, "obstacle");
    storePositiveMemories("alice", "bob", 20);
    phase.run(TENANT, List.of());

    var schema = readSchema("alice");
    assertThat(schema.trust()).isGreaterThanOrEqualTo(0.5);
    assertThat(schema.role()).isEqualTo("stranger");
}






}

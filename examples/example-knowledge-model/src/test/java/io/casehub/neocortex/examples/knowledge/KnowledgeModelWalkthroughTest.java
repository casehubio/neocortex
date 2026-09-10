package io.casehub.neocortex.examples.knowledge;

import io.casehub.neocortex.memory.Subject;
import io.casehub.neocortex.mindmap.EdgeInput;
import io.casehub.neocortex.mindmap.MindMapEdge;
import io.casehub.neocortex.mindmap.MindMapNode;
import io.casehub.neocortex.mindmap.MindMapQuery;
import io.casehub.neocortex.mindmap.MindMapStore;
import io.casehub.neocortex.mindmap.NodeInput;
import io.casehub.neocortex.mindmap.SchemaField;
import io.casehub.neocortex.mindmap.SubgraphInput;
import io.casehub.neocortex.mindmap.SubgraphTypes;
import io.casehub.neocortex.mindmap.inmem.InMemoryMindMapStore;
import io.casehub.neocortex.mindmap.intelligence.Organisational;
import io.casehub.neocortex.mindmap.intelligence.Personable;
import io.casehub.neocortex.mindmap.intelligence.PersonableTraitRule;
import io.casehub.neocortex.mindmap.intelligence.OrganisationalTraitRule;
import io.casehub.neocortex.mindmap.intelligence.TypeRegistry;
import io.casehub.neocortex.thing.Thing;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestMethodOrder;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Walkthrough: how raw notes evolve into semantic knowledge.
 *
 * Scenario — planning Emily's birthday party. A user has a conversation
 * with an LLM. The system captures the conversation as notes, then
 * progressively extracts and enriches semantic knowledge.
 *
 * Each test method is a phase in the knowledge lifecycle:
 *   1. Raw notes — unstructured content captured from conversation
 *   2. Entity extraction — typed entities identified from notes
 *   3. Trait emergence — TraitRules fire as properties accumulate
 *   4. Dynamic types — LLM discovers a new type not in the core set
 *   5. Property schema — schema for the new dynamic type
 *   6. Graph queries — query the enriched knowledge graph
 *   7. Capabilities matrix — summary of what each phase demonstrated
 */
@Tag("smoke")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class KnowledgeModelWalkthroughTest {

    private MindMapStore store;
    private TypeRegistry typeRegistry;
    private static final String TENANT = "family-planner";

    // Node IDs populated during phases
    private String notesSgId;
    private String peopleSgId;
    private String orgSgId;
    private String emilyId;
    private String philId;
    private String johnId;
    private String joeId;
    private String schoolId;
    private String partySgId;
    private String partyId;

    @BeforeAll
    void setUp() {
        store = new InMemoryMindMapStore();
        typeRegistry = new TypeRegistry(store);
    }

    // ── Phase 1: Raw Notes ────────────────────────────────────────────
    //
    // A user has a conversation about planning a birthday party. The
    // system captures the conversation as "general" Things — unstructured
    // notes with freeform properties. No semantic typing yet.

    @Test @Order(1)
    void phase1_rawNotes_capturedAsGeneralThings() {
        notesSgId = store.createSubgraph(
            new SubgraphInput("Conversation Notes", SubgraphTypes.GENERAL, null), TENANT);

        String note1Id = store.addNode(
            NodeInput.of("Party planning chat", notesSgId)
                .withProperties(Map.of(
                    "title", "Planning Emily's birthday",
                    "body", "Emily's birthday is next Saturday. Phil wants to invite " +
                            "John from school. Need to check with Joe (John's dad). " +
                            "Phil is turning 8. Emily mentioned she'd like it at the " +
                            "community hall. John has a nut allergy.")),
            TENANT);

        String note2Id = store.addNode(
            NodeInput.of("Venue research", notesSgId)
                .withProperties(Map.of(
                    "title", "Venue options",
                    "body", "Community hall is available Saturday 2-5pm. " +
                            "Cost is £50. Can hold 30 kids. Has a kitchen.")),
            TENANT);

        // Link the notes — they're about the same topic
        store.addEdge(EdgeInput.of(note1Id, note2Id, "related-to"), TENANT);

        // Notes are Things with type "general" — no semantic weight yet
        Thing note = store.getNode(note1Id, TENANT);
        assertThat(note.type()).isEqualTo(SubgraphTypes.GENERAL);
        assertThat(note.is("Personable")).isFalse();
        assertThat(note.property("body")).isPresent();

        System.out.println("Phase 1: Created " + store.nodesIn(notesSgId, TENANT).size()
            + " note Things of type '" + note.type() + "'");
    }

    // ── Phase 2: Entity Extraction ───────────────────────────────────
    //
    // A near-time process (MindMapExtractor or similar) parses the notes
    // and creates typed entities with properties and relationships.
    // This simulates what a background LLM extraction would produce.

    @Test @Order(2)
    void phase2_entityExtraction_createsTypedThings() {
        peopleSgId = store.createSubgraph(
            new SubgraphInput("People", SubgraphTypes.PERSON, null), TENANT);
        orgSgId = store.createSubgraph(
            new SubgraphInput("Organisations", SubgraphTypes.ORGANISATION, null), TENANT);

        // Emily — extracted with properties
        emilyId = store.addNode(
            NodeInput.of("Emily", peopleSgId)
                .withProperties(Map.of(
                    "role", "mum",
                    "email", "emily@example.com"))
                .withTraits(Set.of("Personable")),
            TENANT);

        // Phil — Emily's son, turning 8
        philId = store.addNode(
            NodeInput.of("Phil", peopleSgId)
                .withProperties(Map.of(
                    "age", "8",
                    "birthday", "next Saturday"))
                .withTraits(Set.of("Personable")),
            TENANT);

        // John — Phil's school friend
        johnId = store.addNode(
            NodeInput.of("John", peopleSgId)
                .withProperties(Map.of(
                    "dietary", "nut allergy"))
                .withTraits(Set.of("Personable")),
            TENANT);

        // Joe — John's dad
        joeId = store.addNode(
            NodeInput.of("Joe", peopleSgId)
                .withTraits(Set.of("Personable")),
            TENANT);

        // St Mary's School
        schoolId = store.addNode(
            NodeInput.of("St Mary's Primary", orgSgId)
                .withProperties(Map.of(
                    "industry", "education",
                    "location", "High Street"))
                .withTraits(Set.of("Organisational")),
            TENANT);

        // Relationships extracted from conversation
        store.addEdge(EdgeInput.of(philId, emilyId, "child-of"), TENANT);
        store.addEdge(EdgeInput.of(johnId, joeId, "child-of"), TENANT);
        store.addEdge(EdgeInput.of(philId, schoolId, "attends"), TENANT);
        store.addEdge(EdgeInput.of(johnId, schoolId, "attends"), TENANT);
        store.addEdge(EdgeInput.of(philId, johnId, "friend-of"), TENANT);

        // Typed entities are Things with semantic types
        Thing emily = store.getNode(emilyId, TENANT);
        assertThat(emily.type()).isEqualTo(SubgraphTypes.PERSON);
        assertThat(emily.name()).isEqualTo("Emily");

        Thing school = store.getNode(schoolId, TENANT);
        assertThat(school.type()).isEqualTo(SubgraphTypes.ORGANISATION);

        System.out.println("Phase 2: Extracted " + store.nodesIn(peopleSgId, TENANT).size()
            + " people, " + store.nodesIn(orgSgId, TENANT).size() + " organisations, "
            + "5 relationships");
    }

    // ── Phase 3: Trait Emergence ─────────────────────────────────────
    //
    // TraitRules evaluate nodes against their properties and edges.
    // Traits are types — when a Thing gains a trait, it gains a type.
    // thing.is("Personable") is an instanceof check.

    @Test @Order(3)
    void phase3_traitEmergence_rulesFireAsPropertiesAccumulate() {
        MindMapNode emily = store.getNode(emilyId, TENANT);
        List<MindMapEdge> emilyEdges = store.neighbors(emilyId, TENANT);

        // TraitRule evaluates whether a node satisfies a trait
        PersonableTraitRule personableRule = new PersonableTraitRule();
        assertThat(personableRule.matches(emily, emilyEdges)).isTrue();

        // Emily already has "Personable" in traits (set during extraction)
        // In production, TraitApplicationDecorator does this automatically
        assertThat(emily.is("Personable")).isTrue();

        // Typed property access via as() — the Drools instanceof + cast pattern
        Personable p = emily.as(Personable.class);
        assertThat(p.role()).contains("mum");
        assertThat(p.email()).contains("emily@example.com");

        // St Mary's satisfies Organisational
        MindMapNode school = store.getNode(schoolId, TENANT);
        OrganisationalTraitRule orgRule = new OrganisationalTraitRule();
        assertThat(orgRule.matches(school, store.neighbors(schoolId, TENANT))).isTrue();

        Organisational org = school.as(Organisational.class);
        assertThat(org.industry()).contains("education");
        assertThat(org.location()).contains("High Street");

        // Traits are types — is() checks the full type set
        assertThat(emily.is(SubgraphTypes.PERSON)).isTrue();    // creation type
        assertThat(emily.is("Personable")).isTrue();              // trait type

        System.out.println("Phase 3: Emily is Personable (role=" + p.role().orElse("?")
            + "), School is Organisational (industry=" + org.industry().orElse("?") + ")");
    }

    // ── Phase 4: Dynamic Types ───────────────────────────────────────
    //
    // The LLM discovers a type not in the core set — "birthday-party".
    // TypeRegistry registers it in the type hierarchy as a subtype
    // of "general". No recompile needed.

    @Test @Order(4)
    void phase4_dynamicTypes_llmDiscoversNewType() {
        // Register a dynamic type — the LLM identified "birthday-party" as a concept
        typeRegistry.registerType("birthday-party", SubgraphTypes.GENERAL, TENANT);

        assertThat(typeRegistry.typeExists("birthday-party", TENANT)).isTrue();
        assertThat(typeRegistry.subtypesOf(SubgraphTypes.GENERAL, TENANT))
            .contains("birthday-party");

        // No java-class for dynamic types — they don't have Java interfaces yet
        assertThat(typeRegistry.javaClass("birthday-party", TENANT)).isEmpty();

        // Core types DO have java-class mappings
        assertThat(typeRegistry.javaClass(SubgraphTypes.PERSON, TENANT))
            .contains(Personable.class);

        // Create a subgraph for the new type and add the party entity
        partySgId = store.createSubgraph(
            new SubgraphInput("Emily's Birthday Party", "birthday-party", null), TENANT);

        partyId = store.addNode(
            NodeInput.of("Phil's 8th Birthday", partySgId)
                .withProperties(Map.of(
                    "date", "next Saturday",
                    "venue", "Community Hall",
                    "time", "2pm-5pm",
                    "budget", "50",
                    "capacity", "30")),
            TENANT);

        // Link the party to people
        store.addEdge(EdgeInput.of(partyId, philId, "celebrates"), TENANT);
        store.addEdge(EdgeInput.of(emilyId, partyId, "organises"), TENANT);

        Thing party = store.getNode(partyId, TENANT);
        assertThat(party.type()).isEqualTo("birthday-party");

        System.out.println("Phase 4: Registered dynamic type 'birthday-party', "
            + "created party entity of type '" + party.type() + "'");
    }

    // ── Phase 5: Property Schema ─────────────────────────────────────
    //
    // Schema for dynamic types is stored as properties on type nodes.
    // Core types derive schema from their Java interfaces automatically.
    // Schema is advisory — it documents expectations, not constraints.

    @Test @Order(5)
    void phase5_propertySchema_schemaForDynamicAndCoreTypes() {
        // Core type schema is derived from Java interface methods
        Map<String, SchemaField> personSchema = typeRegistry.schemaFor(SubgraphTypes.PERSON, TENANT);
        assertThat(personSchema).containsKey("birthday");
        assertThat(personSchema.get("birthday").type()).isEqualTo("string");
        assertThat(personSchema.get("birthday").required()).isFalse();
        assertThat(personSchema).containsKey("role");
        assertThat(personSchema).containsKey("email");

        // Dynamic type schema — not yet defined
        Map<String, SchemaField> partySchema = typeRegistry.schemaFor("birthday-party", TENANT);
        assertThat(partySchema).isEmpty();

        // A background process would discover consistent properties and set schema.
        // For now, we'll add schema manually via the type node's properties.
        // In production, this is done by the consolidation pipeline (#295).

        System.out.println("Phase 5: Person schema has " + personSchema.size()
            + " fields (derived from Personable interface), "
            + "birthday-party schema has " + partySchema.size() + " fields (not yet defined)");
    }

    // ── Phase 6: Graph Queries ───────────────────────────────────────
    //
    // Query the enriched knowledge graph. Search by text, by type,
    // by traits. Traverse relationships. Bridge to Subject for
    // cross-store references.

    @Test @Order(6)
    void phase6_graphQueries_queryEnrichedKnowledge() {
        // Search by text — "who is John?"
        List<MindMapNode> results = store.search(
            MindMapQuery.of(TENANT, 10).withText("John"));
        assertThat(results).extracting(MindMapNode::name)
            .contains("John");

        // Search within a subgraph — "who are the people?"
        List<MindMapNode> people = store.nodesIn(peopleSgId, TENANT);
        assertThat(people).hasSize(4);
        assertThat(people).extracting(Thing::name)
            .containsExactlyInAnyOrder("Emily", "Phil", "John", "Joe");

        // Traverse relationships — "who is connected to Phil?"
        List<MindMapEdge> philEdges = store.neighbors(philId, TENANT);
        assertThat(philEdges).hasSizeGreaterThanOrEqualTo(4); // child-of, attends, friend-of, celebrates

        Set<String> connectedNodeIds = new HashSet<>();
        for (MindMapEdge edge : philEdges) {
            connectedNodeIds.add(edge.sourceNodeId().equals(philId)
                ? edge.targetNodeId() : edge.sourceNodeId());
        }
        // Phil is connected to: Emily (parent), school, John (friend), party
        assertThat(connectedNodeIds).contains(emilyId, schoolId, johnId, partyId);

        // Subject ↔ Thing bridge — convention-based cross-store reference
        Subject emilyRef = Subject.of("person", emilyId);
        Thing resolved = store.getNode(emilyRef.id(), TENANT);
        assertThat(resolved).isNotNull();
        assertThat(resolved.type()).isEqualTo(emilyRef.type());
        assertThat(resolved.name()).isEqualTo("Emily");

        // Query dietary requirements for party planning
        MindMapNode john = store.getNode(johnId, TENANT);
        assertThat(john.property("dietary")).contains("nut allergy");

        System.out.println("Phase 6: Found " + people.size() + " people, "
            + "Phil has " + philEdges.size() + " connections, "
            + "John's dietary: " + john.property("dietary").orElse("none"));
    }

    // ── Phase 7: Capabilities Matrix ─────────────────────────────────

    @Test @Order(7)
    void phase7_capabilitiesMatrix_summaryOfWhatWasDemonstrated() {
        System.out.println();
        System.out.println("═══════════════════════════════════════════════════════════════");
        System.out.println("  Knowledge Model — Capabilities Matrix");
        System.out.println("═══════════════════════════════════════════════════════════════");
        System.out.println();
        System.out.printf("  %-35s %-10s %s%n", "Capability", "Phase", "API");
        System.out.println("  " + "─".repeat(70));
        System.out.printf("  %-35s %-10s %s%n", "Freeform content nodes", "1", "NodeInput.of() + SubgraphTypes.GENERAL");
        System.out.printf("  %-35s %-10s %s%n", "Thing.type() from subgraph", "1,2", "Thing.type() → subgraph type string");
        System.out.printf("  %-35s %-10s %s%n", "Typed entities with properties", "2", "NodeInput.withProperties()");
        System.out.printf("  %-35s %-10s %s%n", "Typed relationships (edges)", "2", "EdgeInput.of() + MindMapStore.addEdge()");
        System.out.printf("  %-35s %-10s %s%n", "Trait evaluation (rules)", "3", "TraitRule.matches(node, edges)");
        System.out.printf("  %-35s %-10s %s%n", "is() — trait type checking", "3", "Thing.is(\"Personable\")");
        System.out.printf("  %-35s %-10s %s%n", "as() — typed property access", "3", "Thing.as(Personable.class)");
        System.out.printf("  %-35s %-10s %s%n", "Traits are types", "3", "is(type) ∪ is(trait) = full type set");
        System.out.printf("  %-35s %-10s %s%n", "Dynamic type registration", "4", "TypeRegistry.registerType()");
        System.out.printf("  %-35s %-10s %s%n", "Type hierarchy (subtype-of)", "4", "TypeRegistry.subtypesOf()");
        System.out.printf("  %-35s %-10s %s%n", "Core type → Java class mapping", "4", "TypeRegistry.javaClass()");
        System.out.printf("  %-35s %-10s %s%n", "Schema from Java interfaces", "5", "TypeRegistry.schemaFor() + reflection");
        System.out.printf("  %-35s %-10s %s%n", "Text search", "6", "MindMapQuery.withText()");
        System.out.printf("  %-35s %-10s %s%n", "Subgraph-scoped queries", "6", "MindMapStore.nodesIn()");
        System.out.printf("  %-35s %-10s %s%n", "Relationship traversal", "6", "MindMapStore.neighbors()");
        System.out.printf("  %-35s %-10s %s%n", "Subject ↔ Thing bridge", "6", "Subject.of(type, id) → store.getNode()");
        System.out.println();
        System.out.println("  Lifecycle: notes → entities → traits → dynamic types → schema → queries");
        System.out.println("  Future: consolidation pipeline (#295) automates phases 2-5 as background processing");
        System.out.println("═══════════════════════════════════════════════════════════════");
    }
}

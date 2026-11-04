package io.casehub.neocortex.examples.corpus.domains.npc;

import io.casehub.neocortex.corpus.management.CorpusEntry;
import io.casehub.neocortex.corpus.management.CorpusLifecycle;
import io.casehub.neocortex.corpus.management.CorpusQuery;
import io.casehub.neocortex.examples.corpus.CorpusTestHarness;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@Tag("smoke")
class NpcScenarioTest {

    private static final String TENANT = "npc-tenant";
    private CorpusTestHarness harness;

    @BeforeEach
    void setUp() {
        harness = CorpusTestHarness.create();
    }

    @Test
    void fullLifecycle() {
        var desc = harness.catalogue().register(
            "darkwood-world", NpcCorpusTypes.NPC_WORLD, TENANT,
            "vec-darkwood-world", List.of(), List.of(), Map.of());
        var corpus = desc.corpusRef();
        assertThat(desc.type().name()).isEqualTo("game-npc-world");

        harness.addEntry(corpus, new CorpusEntry("npc-001",
            Map.of("name", "Elara the Sage", "entity_type", "npc", "location", "Tower of Whispers",
                   "faction", "Seekers Guild",
                   "relationships", List.of("mentor:npc-003", "rival:npc-002"),
                   "dialogue_tags", List.of("quest-giver", "lore-keeper"),
                   "quest_refs", List.of("quest-010", "quest-015")),
            "Elara is the head lorekeeper of the Seekers Guild, researching ancient portal magic.", null));

        harness.addEntry(corpus, new CorpusEntry("npc-002",
            Map.of("name", "Kael Ironheart", "entity_type", "npc", "location", "Forge District",
                   "faction", "Blacksmiths Union",
                   "relationships", List.of("rival:npc-001", "friend:npc-004"),
                   "inventory", List.of("enchanted-hammer", "dragon-scale-ingot"),
                   "dialogue_tags", List.of("merchant", "craftsman")),
            "Kael is the finest blacksmith in the realm, forging weapons from rare materials.", null));

        harness.addEntry(corpus, new CorpusEntry("npc-003",
            Map.of("name", "Old Merrick", "entity_type", "npc", "location", "Hermit's Cave",
                   "relationships", List.of("student:npc-001"),
                   "dialogue_tags", List.of("retired-adventurer")),
            "Old Merrick was once a renowned adventurer who now lives in seclusion.", null));

        harness.addEntry(corpus, new CorpusEntry("loc-001",
            Map.of("name", "Tower of Whispers", "entity_type", "location", "location", "Northern Mountains",
                   "faction", "Seekers Guild"),
            "A tall spire atop the northern cliffs, the Tower of Whispers houses the Seekers Guild library.", null));

        harness.addEntry(corpus, new CorpusEntry("item-001",
            Map.of("name", "Dragon Scale Ingot", "entity_type", "item", "location", "Forge District",
                   "quest_refs", List.of("quest-010")),
            "A rare crafting material extracted from dragon scales, used to forge legendary weapons.", null));

        var results = harness.search().search(corpus, CorpusQuery.of("blacksmith"));
        assertThat(results).isNotEmpty();
        assertThat(results.getFirst().entry().id()).isEqualTo("npc-002");

        var locationResults = harness.search().search(corpus, CorpusQuery.of("tower"));
        assertThat(locationResults).extracting(r -> r.entry().stringField("entity_type"))
            .contains("location");

        var retrieved = harness.entryStore().get(corpus, "npc-001");
        assertThat(retrieved).isPresent();
        assertThat(retrieved.get().stringListField("relationships"))
            .containsExactly("mentor:npc-003", "rival:npc-002");
        assertThat(retrieved.get().stringListField("quest_refs"))
            .containsExactly("quest-010", "quest-015");

        harness.catalogue().updateLifecycle(corpus, CorpusLifecycle.ARCHIVED);
        assertThat(harness.catalogue().list(TENANT, CorpusLifecycle.ACTIVE)).isEmpty();
        harness.catalogue().updateLifecycle(corpus, CorpusLifecycle.ACTIVE);
        assertThat(harness.catalogue().list(TENANT, CorpusLifecycle.ACTIVE)).hasSize(1);
    }
}

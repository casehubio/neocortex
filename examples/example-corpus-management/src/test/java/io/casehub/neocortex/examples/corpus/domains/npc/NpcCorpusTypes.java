package io.casehub.neocortex.examples.corpus.domains.npc;

import io.casehub.neocortex.corpus.management.CorpusType;
import io.casehub.neocortex.corpus.management.FieldSchema;

import java.util.Map;

public final class NpcCorpusTypes {

    public static final CorpusType NPC_WORLD = new CorpusType(
        "game-npc-world",
        Map.of(
            "id", FieldSchema.STRING,
            "name", FieldSchema.STRING,
            "entity_type", FieldSchema.STRING,
            "location", FieldSchema.STRING
        ),
        Map.of(
            "relationships", FieldSchema.STRING_LIST,
            "faction", FieldSchema.STRING,
            "inventory", FieldSchema.STRING_LIST,
            "dialogue_tags", FieldSchema.STRING_LIST,
            "quest_refs", FieldSchema.STRING_LIST
        ),
        null
    );

    private NpcCorpusTypes() {}
}

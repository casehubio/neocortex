package io.casehub.neocortex.mindmap;

public final class RelationshipVocabulary {

    public static final MindMapVocabulary RELATIONSHIP_VOCABULARY = MindMapVocabulary.builder()
            .edgeType("trusts", 30.0, "has-faith-in")
            .edgeType("distrusts", 14.0, "suspects", "wary-of")
            .edgeType("fears", 14.0, "dreads", "avoids")
            .edgeType("admires", 60.0, "respects", "looks-up-to")
            .edgeType("resents", 21.0, "begrudges")
            .edgeType("protects", 60.0, "shields", "guards")
            .edgeType("exploits", 21.0, "manipulates")
            .edgeType("relies-on", 30.0, "leans-on", "emotionally-dependent")
            .build();

    private RelationshipVocabulary() {}
}

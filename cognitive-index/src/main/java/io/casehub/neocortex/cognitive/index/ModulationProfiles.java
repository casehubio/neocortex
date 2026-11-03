package io.casehub.neocortex.cognitive.index;

import io.casehub.neocortex.cognitive.Confidence;
import io.casehub.neocortex.cognitive.ModulationProfile;
import io.casehub.neocortex.corpus.management.CorpusSearchResult;
import io.casehub.neocortex.memory.Memory;
import io.casehub.neocortex.mindmap.MindMapNode;

public final class ModulationProfiles {

    private ModulationProfiles() {}

    public static final ModulationProfile<Memory> MEMORY = new ModulationProfile<>(
        Memory::confidence,
        Memory::pleasure,
        Memory::arousal,
        Memory::dominance,
        Memory::createdAt
    );

    public static final ModulationProfile<MindMapNode> NODE = new ModulationProfile<>(
        MindMapNode::confidence,
        MindMapNode::pleasure,
        MindMapNode::arousal,
        MindMapNode::dominance,
        MindMapNode::createdAt
    );
    public static final ModulationProfile<CorpusSearchResult> CORPUS = new ModulationProfile<>(
            r -> Confidence.unknown(1.0),
            r -> 0.0,
            r -> 0.0,
            r -> 0.0,
            CorpusSearchResult::entryTimestamp
    );

}

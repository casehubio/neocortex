package io.casehub.neocortex.mindmap.runtime;

import io.casehub.neocortex.mindmap.AbstractForwardingMindMapStore;
import io.casehub.neocortex.mindmap.MindMapStore;

public class VocabularyNormalizationDecorator extends AbstractForwardingMindMapStore {

    public VocabularyNormalizationDecorator(MindMapStore delegate) {
        super(delegate);
    }
}

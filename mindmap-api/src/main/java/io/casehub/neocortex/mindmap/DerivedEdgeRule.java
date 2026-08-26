package io.casehub.neocortex.mindmap;

import java.util.List;

public interface DerivedEdgeRule {

    String PROPERTY_DERIVED = "mindmap.derived";
    String PROPERTY_TRIGGER_EDGE_ID = "mindmap.derived.trigger-edge-id";
    String PROPERTY_RULE_NAME = "mindmap.derived.rule-name";

    String name();

    List<EdgeInput> derive(MindMapNode sourceNode, MindMapEdge trigger, MindMapStore store);
}

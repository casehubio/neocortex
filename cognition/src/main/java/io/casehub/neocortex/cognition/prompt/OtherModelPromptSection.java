package io.casehub.neocortex.cognition.prompt;

import io.casehub.neocortex.mindmap.MindMapStore;
import org.jspecify.annotations.Nullable;

public class OtherModelPromptSection implements CognitionPromptRenderer {

    private final MindMapStore mindMapStore;

    public OtherModelPromptSection(MindMapStore mindMapStore) {
        this.mindMapStore = mindMapStore;
    }

    @Override
    public @Nullable String render(CognitionRenderContext context) {
        if (context.subjectId() == null) {
            return null;
        }
        var node = mindMapStore.listSubgraphs(context.tenantId()).stream()
                               .filter(s -> "cognitive".equals(s.type()))
                               .flatMap(s -> mindMapStore.nodesIn(s.id(), context.tenantId()).stream())
                               .filter(n -> n.traits().contains("OtherModelDerived"))
                               .filter(n -> context.agentId().equals(n.properties().get("agent-id")))
                               .filter(n -> context.subjectId().equals(n.properties().get("target-id")))
                               .findFirst()
                               .orElse(null);
        if (node == null) {
            return null;
        }

        var parts   = new java.util.ArrayList<String>();
        var sensing = node.properties().get("empathic-sensing");
        if (sensing != null && !sensing.isBlank()) {
            parts.add(sensing);
        }
        var bond = node.properties().get("relational-understanding");
        if (bond != null && !bond.isBlank()) {
            parts.add(bond);
        }

        return parts.isEmpty() ? null : "What You Sense About " + context.subjectId() + ":\n" + String.join("\n\n", parts);
    }
@Override
public BlockTag blockTag() {
    return BlockTag.OTHER_MODEL;
}

}

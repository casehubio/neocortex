package io.casehub.neocortex.cognition.prompt;

import io.casehub.eidos.api.PromptBlock;
import io.casehub.eidos.api.PromptTier;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

public interface CognitiveLifecycle {

    default void initialize(String agentId, String tenantId) {}

    void tick(String agentId, String tenantId, Set<String> activeSubjects);

    default @Nullable String evaluateProactive(String agentId, String tenantId,
                                                String channelContext) {
        return null;
    }

    void recordInteraction(String agentId, String tenantId,
                           @Nullable String subjectId,
                           String userMessage, String response);

    List<CognitionPromptRenderer> promptSections();

    default List<PromptBlock> contributeBlocks(CognitionRenderContext context) {
        var blocks = new ArrayList<PromptBlock>();
        for (var renderer : promptSections()) {
            try {
                var content = renderer.render(context);
                if (content != null && !content.isBlank()) {
                    var tag = renderer.blockTag();
                    var tagName = tag != null ? tag.name() : renderer.getClass().getSimpleName();
                    blocks.add(new PromptBlock(PromptTier.COGNITIVE, tagName, 0f, content));
                }
            } catch (Exception e) {
                // skip sections with unresolvable dependencies — matches CognitionCore behaviour
            }
        }
        return List.copyOf(blocks);
    }
}

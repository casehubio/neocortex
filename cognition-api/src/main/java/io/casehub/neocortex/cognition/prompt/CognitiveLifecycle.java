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
                    var simpleName = renderer.getClass().getSimpleName();
                    var tagName = tag != null ? tag.name()
                            : simpleName.isEmpty() ? renderer.getClass().getName() : simpleName;
                    var salience = salienceOf(renderer.sectionTier());
                    blocks.add(new PromptBlock(PromptTier.COGNITIVE, tagName, salience, content));
                }
            } catch (Exception e) {
                // skip sections with unresolvable dependencies — matches CognitionCore behaviour
            }
        }
        return List.copyOf(blocks);
    }

    private static float salienceOf(@org.jspecify.annotations.Nullable SectionTier tier) {
        if (tier == null) return 0.5f;
        return switch (tier) {
            case CORE -> 1.0f;
            case CONTEXTUAL -> 0.6f;
            case SUPPLEMENTARY -> 0.3f;
        };
    }
}

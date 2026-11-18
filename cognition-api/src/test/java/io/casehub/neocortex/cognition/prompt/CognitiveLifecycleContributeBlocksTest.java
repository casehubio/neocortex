package io.casehub.neocortex.cognition.prompt;

import io.casehub.eidos.api.PromptBlock;
import io.casehub.eidos.api.PromptTier;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class CognitiveLifecycleContributeBlocksTest {

    private static CognitiveLifecycle lifecycleWith(List<CognitionPromptRenderer> sections) {
        return new CognitiveLifecycle() {
            @Override
            public void tick(String agentId, String tenantId,
                             Set<String> activeSubjects) {}

            @Override
            public void recordInteraction(String agentId, String tenantId,
                                          @Nullable String subjectId,
                                          String userMessage, String response) {}

            @Override
            public List<CognitionPromptRenderer> promptSections() {
                return sections;
            }
        };
    }

    @Test
    void wrapsRendererOutputInCognitivePromptBlock() {
        var renderer = new CognitionPromptRenderer() {
            @Override
            public @Nullable String render(CognitionRenderContext context) {
                return "You feel calm.";
            }

            @Override
            public BlockTag blockTag() {
                return BlockTag.MOOD;
            }
        };
        var lifecycle = lifecycleWith(List.of(renderer));
        var ctx = new CognitionRenderContext("agent-1", "tenant-1", null);

        var blocks = lifecycle.contributeBlocks(ctx);

        assertThat(blocks).hasSize(1);
        assertThat(blocks.get(0).tier()).isEqualTo(PromptTier.COGNITIVE);
        assertThat(blocks.get(0).tag()).isEqualTo("MOOD");
        assertThat(blocks.get(0).salience()).isEqualTo(0f);
        assertThat(blocks.get(0).content()).isEqualTo("You feel calm.");
    }

    @Test
    void skipsNullRendererOutput() {
        var renderer = new CognitionPromptRenderer() {
            @Override
            public @Nullable String render(CognitionRenderContext context) {
                return null;
            }

            @Override
            public BlockTag blockTag() {
                return BlockTag.MOOD;
            }
        };
        var lifecycle = lifecycleWith(List.of(renderer));
        var ctx = new CognitionRenderContext("agent-1", "tenant-1", null);

        assertThat(lifecycle.contributeBlocks(ctx)).isEmpty();
    }

    @Test
    void skipsBlankRendererOutput() {
        var renderer = new CognitionPromptRenderer() {
            @Override
            public @Nullable String render(CognitionRenderContext context) {
                return "   ";
            }

            @Override
            public BlockTag blockTag() {
                return BlockTag.MOOD;
            }
        };
        var lifecycle = lifecycleWith(List.of(renderer));
        var ctx = new CognitionRenderContext("agent-1", "tenant-1", null);

        assertThat(lifecycle.contributeBlocks(ctx)).isEmpty();
    }

    @Test
    void usesClassSimpleNameWhenBlockTagIsNull() {
        var renderer = new CognitionPromptRenderer() {
            @Override
            public @Nullable String render(CognitionRenderContext context) {
                return "Some content.";
            }
        };
        var lifecycle = lifecycleWith(List.of(renderer));
        var ctx = new CognitionRenderContext("agent-1", "tenant-1", null);

        var blocks = lifecycle.contributeBlocks(ctx);

        assertThat(blocks).hasSize(1);
        assertThat(blocks.get(0).tag()).isNotNull();
        assertThat(blocks.get(0).tier()).isEqualTo(PromptTier.COGNITIVE);
    }

    @Test
    void preservesInsertionOrderAcrossMultipleRenderers() {
        var mood = new CognitionPromptRenderer() {
            @Override
            public @Nullable String render(CognitionRenderContext context) {
                return "Mood content.";
            }

            @Override
            public BlockTag blockTag() {
                return BlockTag.MOOD;
            }
        };
        var drives = new CognitionPromptRenderer() {
            @Override
            public @Nullable String render(CognitionRenderContext context) {
                return "Drive content.";
            }

            @Override
            public BlockTag blockTag() {
                return BlockTag.DRIVES;
            }
        };
        var nullSection = new CognitionPromptRenderer() {
            @Override
            public @Nullable String render(CognitionRenderContext context) {
                return null;
            }

            @Override
            public BlockTag blockTag() {
                return BlockTag.NARRATIVE;
            }
        };
        var lifecycle = lifecycleWith(List.of(mood, nullSection, drives));
        var ctx = new CognitionRenderContext("agent-1", "tenant-1", null);

        var blocks = lifecycle.contributeBlocks(ctx);

        assertThat(blocks).hasSize(2);
        assertThat(blocks.get(0).tag()).isEqualTo("MOOD");
        assertThat(blocks.get(1).tag()).isEqualTo("DRIVES");
    }

    @Test
    void returnsUnmodifiableList() {
        var renderer = new CognitionPromptRenderer() {
            @Override
            public @Nullable String render(CognitionRenderContext context) {
                return "Content.";
            }

            @Override
            public BlockTag blockTag() {
                return BlockTag.MOOD;
            }
        };
        var lifecycle = lifecycleWith(List.of(renderer));
        var ctx = new CognitionRenderContext("agent-1", "tenant-1", null);

        var blocks = lifecycle.contributeBlocks(ctx);

        assertThat(blocks).isUnmodifiable();
    }

    @Test
    void skipsRenderersThatThrowExceptions() {
        var failing = new CognitionPromptRenderer() {
            @Override
            public @Nullable String render(CognitionRenderContext context) {
                throw new RuntimeException("unresolvable dependency");
            }

            @Override
            public BlockTag blockTag() {
                return BlockTag.MOOD;
            }
        };
        var working = new CognitionPromptRenderer() {
            @Override
            public @Nullable String render(CognitionRenderContext context) {
                return "Drive content.";
            }

            @Override
            public BlockTag blockTag() {
                return BlockTag.DRIVES;
            }
        };
        var lifecycle = lifecycleWith(List.of(failing, working));
        var ctx = new CognitionRenderContext("agent-1", "tenant-1", null);

        var blocks = lifecycle.contributeBlocks(ctx);

        assertThat(blocks).hasSize(1);
        assertThat(blocks.get(0).tag()).isEqualTo("DRIVES");
    }
}

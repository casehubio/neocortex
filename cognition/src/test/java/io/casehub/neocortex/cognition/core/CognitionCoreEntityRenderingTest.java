package io.casehub.neocortex.cognition.core;

import io.casehub.neocortex.cognition.mood.MoodConfig;
import io.casehub.neocortex.cognition.mood.MoodOrchestrator;
import io.casehub.neocortex.cognition.prompt.CognitionPromptRenderer;
import io.casehub.neocortex.cognition.prompt.CognitionRenderContext;
import io.casehub.neocortex.cognition.prompt.EntityRenderingSection;
import io.casehub.neocortex.entity.token.EntityIdentity;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import java.util.Set;

class CognitionCoreEntityRenderingTest {

    private static final CognitionRenderContext CTX =
            new CognitionRenderContext("agent-1", "tenant-1", null, List.of());

    private static final EntityIdentity FRIDA = EntityIdentity.of(
            "p1", "Frida Kahlo",
            Map.of("canonicalFirst", "Frida"),
            Map.of(), Map.of(), Set.of());

    @Test
    void configureEntityRenderingWrapsSections() {
        var mood = new MoodOrchestrator(MoodConfig.defaults());
        var core = new CognitionCore(mood, null, null, null, null, null, null, null);

        core.configureEntityRendering((entityId, tenantId) ->
                "p1".equals(entityId) ? FRIDA : null);

        core.tick("agent-1", "tenant-1", null, (a, t) -> Set.of());

        var sections = core.promptSections();
        assertThat(sections).allSatisfy(s ->
                assertThat(s).isInstanceOf(EntityRenderingSection.class));
    }

    @Test
    void configureEntityRenderingWithNullLookupStillWraps() {
        var mood = new MoodOrchestrator(MoodConfig.defaults());
        var core = new CognitionCore(mood, null, null, null, null, null, null, null);

        core.configureEntityRendering(null);

        core.tick("agent-1", "tenant-1", null, (a, t) -> Set.of());

        var sections = core.promptSections();
        assertThat(sections).allSatisfy(s ->
                assertThat(s).isInstanceOf(EntityRenderingSection.class));
    }

    @Test
    void entityRenderingCustomizerChainsWithExistingCustomizers() {
        var mood = new MoodOrchestrator(MoodConfig.defaults());
        var core = new CognitionCore(mood, null, null, null, null, null, null, null);

        CognitionPromptRenderer extra = ctx -> "extra content";
        core.chainSectionCustomizer(sections -> {
            sections.add(extra);
            return sections;
        });

        core.configureEntityRendering((entityId, tenantId) ->
                                              "p1".equals(entityId) ? FRIDA : null);

        core.tick("agent-1", "tenant-1", null, (a, t) -> Set.of());

        var sections = core.promptSections();
        assertThat(sections).allSatisfy(s ->
                                                assertThat(s).isInstanceOf(EntityRenderingSection.class));
        assertThat(sections.size()).isGreaterThan(1);
    }
}

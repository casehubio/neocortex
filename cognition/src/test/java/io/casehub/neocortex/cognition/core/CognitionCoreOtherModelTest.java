package io.casehub.neocortex.cognition.core;

import io.casehub.neocortex.cognition.mood.MoodConfig;
import io.casehub.neocortex.cognition.mood.MoodOrchestrator;
import io.casehub.neocortex.cognition.prompt.OtherModelPromptSection;
import io.casehub.neocortex.mindmap.inmem.InMemoryMindMapStore;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class CognitionCoreOtherModelTest {

    @Test
    void tickStoresLastTargetAgentId() {
        var mood = new MoodOrchestrator(MoodConfig.defaults());
        var core = new CognitionCore(mood, null, null, null, null, null, null, null);

        core.tick("agent1", "t1", null, (a, t) -> Set.of(), "obs", "target42");

        assertThat(core.lastTargetAgentId()).isEqualTo("target42");
    }

    @Test
    void lastTargetAgentIdNullBeforeTick() {
        var mood = new MoodOrchestrator(MoodConfig.defaults());
        var core = new CognitionCore(mood, null, null, null, null, null, null, null);

        assertThat(core.lastTargetAgentId()).isNull();
    }

    @Test
    void promptSectionsIncludesOtherModelWhenEnabled() {
        var mood = new MoodOrchestrator(MoodConfig.defaults());
        var store = new InMemoryMindMapStore();
        var config = CognitionConfig.all().with("otherModel", true);
        var core = new CognitionCore(mood, null, null, null, null, null, null, null,
            null, config, store, null, null, null, null, null, null, null);

        core.tick("agent1", "t1", null, (a, t) -> Set.of());

        assertThat(core.promptSections())
            .anyMatch(s -> s instanceof OtherModelPromptSection);
    }

    @Test
    void promptSectionsExcludesOtherModelWhenDisabled() {
        var mood = new MoodOrchestrator(MoodConfig.defaults());
        var store = new InMemoryMindMapStore();
        var config = CognitionConfig.all().with("otherModel", false);
        var core = new CognitionCore(mood, null, null, null, null, null, null, null,
            null, config, store, null, null, null, null, null, null, null);

        core.tick("agent1", "t1", null, (a, t) -> Set.of());

        assertThat(core.promptSections())
            .noneMatch(s -> s instanceof OtherModelPromptSection);
    }

    @Test
    void promptSectionsExcludesOtherModelWhenNoMindMapStore() {
        var mood = new MoodOrchestrator(MoodConfig.defaults());
        var config = CognitionConfig.all().with("otherModel", true);
        var core = new CognitionCore(mood, null, null, null, null, null, null, null,
            null, config, null, null, null, null, null, null, null, null);

        core.tick("agent1", "t1", null, (a, t) -> Set.of());

        assertThat(core.promptSections())
            .noneMatch(s -> s instanceof OtherModelPromptSection);
    }
}

package io.casehub.neocortex.memory.seeding.biography;

import io.casehub.neocortex.mindmap.MindMapQuery;
import io.casehub.neocortex.mindmap.SubgraphTypes;
import io.casehub.neocortex.mindmap.inmem.InMemoryMindMapStore;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class GoalHandlerTest {

    @Test
    void createsGoalNodeWithSubGoals() {
        var store = new InMemoryMindMapStore();
        var handler = new GoalHandler(store);

        var subGoal = new GoalEntry.SubGoal("intl-recognition", "STRATEGIC",
            "Gain recognition beyond Mexico");
        var entry = new GoalEntry("artistic-legacy", null, null,
            "Establish artistic legacy", "THEMATIC", "aspirational",
            List.of(), new GoalEntry.PadValues(0.5, 0.6, 0.7),
            List.of(subGoal));

        handler.handle(profileWith(List.of(entry)), "agent-1", "tenant-1");

        var nodes = store.search(
            MindMapQuery.of("tenant-1", 10).withType(SubgraphTypes.GOAL));
        assertThat(nodes).hasSizeGreaterThanOrEqualTo(2);
        var parent = nodes.stream()
            .filter(n -> n.name().equals("Establish artistic legacy")).findFirst();
        assertThat(parent).isPresent();
        assertThat(parent.get().properties()).containsEntry("tier", "THEMATIC");
        assertThat(parent.get().pleasure()).isEqualTo(0.5);
    }

    @Test
    void skipsExistingGoal() {
        var store = new InMemoryMindMapStore();
        var handler = new GoalHandler(store);

        var entry = new GoalEntry("g1", null, null,
            "Test goal", "OPERATIONAL", "short",
            List.of(), null, List.of());
        var profile = profileWith(List.of(entry));

        handler.handle(profile, "agent-1", "tenant-1");
        handler.handle(profile, "agent-1", "tenant-1");

        var nodes = store.search(
            MindMapQuery.of("tenant-1", 10).withType(SubgraphTypes.GOAL));
        assertThat(nodes).hasSize(1);
    }

    private BiographyProfile profileWith(List<GoalEntry> goals) {
        return new BiographyProfile("a1", "t1",
            List.of(), List.of(), List.of(), List.of(), List.of(),
            List.of(), List.of(), goals, List.of(), List.of());
    }
}

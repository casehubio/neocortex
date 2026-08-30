package io.casehub.neocortex.memory.experience;

import io.casehub.neocortex.memory.MemoryDomain;
import io.casehub.neocortex.memory.MemoryInput;
import org.junit.jupiter.api.Test;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;

class ExperienceEventsTest {

    @Test
    void domainIsExperience() {
        assertEquals(new MemoryDomain("experience"), ExperienceEvents.DOMAIN);
    }

    @Test
    void observationToMemoryInput() {
        var obs = new Observation("agent-1", "tenant-1", "case-1", "turn-42",
                                  null, "PR #123 has merge conflicts", 0.7, Map.of(), "PR #123");
        MemoryInput input = ExperienceEvents.toMemoryInput(obs);

        assertEquals("agent-1", input.entityId());
        assertEquals(ExperienceEvents.DOMAIN, input.domain());
        assertEquals("tenant-1", input.tenantId());
        assertEquals("case-1", input.caseId());
        assertEquals("PR #123 has merge conflicts", input.text());
        assertEquals(0.7, input.confidence().value());
        assertEquals("observation", input.attributes().get(ExperienceAttributeKeys.EVENT_TYPE));
        assertEquals("turn-42", input.attributes().get(ExperienceAttributeKeys.TURN_ID));
        assertEquals("PR #123", input.attributes().get(ExperienceAttributeKeys.SUBJECT));
    }

    @Test
    void actionToMemoryInput() {
        var action = new Action("agent-1", "tenant-1", null, null,
                                null, "Reviewing PR #123", null, Map.of(), "code-review");
        MemoryInput input = ExperienceEvents.toMemoryInput(action);

        assertEquals("action", input.attributes().get(ExperienceAttributeKeys.EVENT_TYPE));
        assertEquals("code-review", input.attributes().get(ExperienceAttributeKeys.CAPABILITY));
        assertFalse(input.attributes().containsKey(ExperienceAttributeKeys.TURN_ID));
    }

    @Test
    void actionWithNullCapabilityOmitsKey() {
        var action = new Action("a1", "t1", null, null, null, "desc", null, Map.of(), null);
        MemoryInput input = ExperienceEvents.toMemoryInput(action);
        assertFalse(input.attributes().containsKey(ExperienceAttributeKeys.CAPABILITY));
    }

    @Test
    void outcomeToMemoryInput() {
        var outcome = new Outcome("agent-1", "tenant-1", "case-1", "turn-42",
                                  null, "Review complete, 3 issues found", 0.8, Map.of(), "completed", "code-review");
        MemoryInput input = ExperienceEvents.toMemoryInput(outcome);

        assertEquals("outcome", input.attributes().get(ExperienceAttributeKeys.EVENT_TYPE));
        assertEquals("completed", input.attributes().get(ExperienceAttributeKeys.RESULT));
        assertEquals("code-review", input.attributes().get(ExperienceAttributeKeys.CAPABILITY));
        assertEquals("turn-42", input.attributes().get(ExperienceAttributeKeys.TURN_ID));
    }

    @Test
    void callerMetadataIsMerged() {
        var obs = new Observation("a1", "t1", null, null, null, "desc", null,
                                  Map.of("custom-key", "custom-value"), "subj");
        MemoryInput input = ExperienceEvents.toMemoryInput(obs);
        assertEquals("custom-value", input.attributes().get("custom-key"));
        assertEquals("observation", input.attributes().get(ExperienceAttributeKeys.EVENT_TYPE));
    }

    @Test
    void metadataKeyCollisionOnEventTypeThrows() {
        var obs = new Observation("a1", "t1", null, null, null, "desc", null,
                                  Map.of(ExperienceAttributeKeys.EVENT_TYPE, "my-custom"), "subj");
        assertThrows(IllegalArgumentException.class, () ->
            ExperienceEvents.toMemoryInput(obs));
    }

    @Test
    void metadataCollisionOnSubjectThrows() {
        var obs = new Observation("a1", "t1", null, null, null, "desc", null,
                                  Map.of(ExperienceAttributeKeys.SUBJECT, "override"), "subj");
        assertThrows(IllegalArgumentException.class, () ->
            ExperienceEvents.toMemoryInput(obs));
    }

    @Test
    void metadataCollisionOnTurnIdThrows() {
        var obs = new Observation("a1", "t1", null, "turn-1", null, "desc", null,
                                  Map.of(ExperienceAttributeKeys.TURN_ID, "override"), "subj");
        assertThrows(IllegalArgumentException.class, () ->
            ExperienceEvents.toMemoryInput(obs));
    }

    @Test
    void nonReservedKeyWithSameNameAsUnusedReservedKeyIsAllowed() {
        var action = new Action("a1", "t1", null, null, null, "desc", null,
                                Map.of(ExperienceAttributeKeys.SUBJECT, "ok-here"), null);
        MemoryInput input = ExperienceEvents.toMemoryInput(action);
        assertEquals("ok-here", input.attributes().get(ExperienceAttributeKeys.SUBJECT));
    }
}

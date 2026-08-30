package io.casehub.neocortex.memory.experience;

import org.junit.jupiter.api.Test;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;

class ExperienceEventTest {

    @Test
    void observationRequiresAgentId() {
        assertThrows(NullPointerException.class, () ->
            new Observation(null, "t1", null, null, null, "desc", null, Map.of(), "subj"));
    }

    @Test
    void observationRequiresTenantId() {
        assertThrows(NullPointerException.class, () ->
            new Observation("a1", null, null, null, null, "desc", null, Map.of(), "subj"));
    }

    @Test
    void observationRequiresDescription() {
        assertThrows(NullPointerException.class, () ->
            new Observation("a1", "t1", null, null, null, null, null, Map.of(), "subj"));
    }

    @Test
    void observationRejectsBlankDescription() {
        assertThrows(IllegalArgumentException.class, () ->
            new Observation("a1", "t1", null, null, null, "  ", null, Map.of(), "subj"));
    }

    @Test
    void observationRequiresSubject() {
        assertThrows(NullPointerException.class, () ->
            new Observation("a1", "t1", null, null, null, "desc", null, Map.of(), null));
    }

    @Test
    void observationRejectsBlankSubject() {
        assertThrows(IllegalArgumentException.class, () ->
            new Observation("a1", "t1", null, null, null, "desc", null, Map.of(), " "));
    }

    @Test
    void observationAcceptsNullOptionalFields() {
        var obs = new Observation("a1", "t1", null, null, null, "desc", null, Map.of(), "subj");
        assertNull(obs.caseId());
        assertNull(obs.turnId());
        assertNull(obs.confidence());
    }

    @Test
    void observationCopiesMetadata() {
        var mutable = new java.util.HashMap<>(Map.of("k", "v"));
        var obs = new Observation("a1", "t1", null, null, null, "desc", null, mutable, "subj");
        mutable.put("k2", "v2");
        assertFalse(obs.metadata().containsKey("k2"));
    }

    @Test
    void observationRejectsInvalidConfidence() {
        assertThrows(IllegalArgumentException.class, () ->
            new Observation("a1", "t1", null, null, null, "desc", 1.1, Map.of(), "subj"));
        assertThrows(IllegalArgumentException.class, () ->
            new Observation("a1", "t1", null, null, null, "desc", -0.1, Map.of(), "subj"));
    }

    @Test
    void observationImplementsExperienceEvent() {
        ExperienceEvent event = new Observation("a1", "t1", "c1", "turn-1", null, "saw it", 0.5, Map.of(), "PR #1");
        assertEquals("a1", event.agentId());
        assertEquals("t1", event.tenantId());
        assertEquals("c1", event.caseId());
        assertEquals("turn-1", event.turnId());
        assertEquals("saw it", event.description());
        assertEquals(0.5, event.confidence());
    }

    @Test
    void actionAcceptsNullCapability() {
        var action = new Action("a1", "t1", null, null, null, "did it", null, Map.of(), null);
        assertNull(action.capability());
    }

    @Test
    void actionRequiresAgentIdAndDescription() {
        assertThrows(NullPointerException.class, () ->
            new Action(null, "t1", null, null, null, "desc", null, Map.of(), null));
        assertThrows(NullPointerException.class, () ->
            new Action("a1", "t1", null, null, null, null, null, Map.of(), null));
    }

    @Test
    void outcomeRequiresResult() {
        assertThrows(NullPointerException.class, () ->
            new Outcome("a1", "t1", null, null, null, "desc", null, Map.of(), null, null));
    }

    @Test
    void outcomeRejectsBlankResult() {
        assertThrows(IllegalArgumentException.class, () ->
            new Outcome("a1", "t1", null, null, null, "desc", null, Map.of(), " ", null));
    }

    @Test
    void outcomeAcceptsNullCapability() {
        var outcome = new Outcome("a1", "t1", null, null, null, "desc", null, Map.of(), "completed", null);
        assertNull(outcome.capability());
    }

    @Test
    void sealedHierarchyIsExhaustive() {
        ExperienceEvent obs = new Observation("a1", "t1", null, null, null, "desc", null, Map.of(), "subj");
        ExperienceEvent act = new Action("a1", "t1", null, null, null, "desc", null, Map.of(), "cap");
        ExperienceEvent out = new Outcome("a1", "t1", null, null, null, "desc", null, Map.of(), "ok", null);

        assertEquals("obs", switch (obs) {
            case Observation o -> "obs";
            case Action a -> "act";
            case Outcome o -> "out";
        });

        assertEquals("act", switch (act) {
            case Observation o -> "obs";
            case Action a -> "act";
            case Outcome o -> "out";
        });

        assertEquals("out", switch (out) {
            case Observation o -> "obs";
            case Action a -> "act";
            case Outcome o -> "out";
        });
    }
}

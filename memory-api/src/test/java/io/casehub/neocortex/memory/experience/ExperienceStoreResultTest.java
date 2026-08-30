package io.casehub.neocortex.memory.experience;

import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;

class ExperienceStoreResultTest {

    @Test
    void emptyResultHasNoStoredOrFailures() {
        var result = ExperienceStoreResult.empty();
        assertTrue(result.stored().isEmpty());
        assertTrue(result.failures().isEmpty());
        assertTrue(result.allSucceeded());
    }

    @Test
    void allSucceededWhenNoFailures() {
        var result = new ExperienceStoreResult(List.of("id-1", "id-2"), List.of());
        assertTrue(result.allSucceeded());
    }

    @Test
    void notAllSucceededWhenFailuresExist() {
        var event = new Action("a1", "t1", null, null, null, "desc", null, Map.of(), null);
        var failure = new ExperienceStoreFailure(1, event, new RuntimeException("boom"));
        var result = new ExperienceStoreResult(List.of("id-1"), List.of(failure));
        assertFalse(result.allSucceeded());
    }

    @Test
    void storedListIsDefensivelyCopied() {
        var mutable = new java.util.ArrayList<>(List.of("id-1"));
        var result = new ExperienceStoreResult(mutable, List.of());
        mutable.add("id-2");
        assertEquals(1, result.stored().size());
    }

    @Test
    void experienceRecordedCarriesEventAndMemoryId() {
        var obs = new Observation("a1", "t1", null, null, null, "desc", null, Map.of(), "subj");
        var recorded = new ExperienceRecorded(obs, "mem-123");
        assertSame(obs, recorded.event());
        assertEquals("mem-123", recorded.memoryId());
    }

    @Test
    void storeFailureCarriesIndexEventAndCause() {
        var action = new Action("a1", "t1", null, null, null, "desc", null, Map.of(), null);
        var cause = new RuntimeException("timeout");
        var failure = new ExperienceStoreFailure(2, action, cause);
        assertEquals(2, failure.inputIndex());
        assertSame(action, failure.event());
        assertSame(cause, failure.cause());
    }
}

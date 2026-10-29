package io.casehub.neocortex.memory.cbr;

import io.casehub.neocortex.memory.Memory;
import io.casehub.neocortex.memory.MemoryAttributeKeys;
import io.casehub.neocortex.memory.MemoryDomain;
import io.casehub.neocortex.memory.MemoryInput;
import io.casehub.neocortex.memory.Subject;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class CbrCaseEntryTest {

    private static final Subject SUBJECT = Subject.of("agent", "a1");
    private static final String TENANT = "t1";

    @Test
    void constructsWithAllFields() {
        var entry = new CbrCaseEntry("server timeout", "add retry logic", "resolved", 0.85);
        assertEquals("server timeout", entry.problem());
        assertEquals("add retry logic", entry.solution());
        assertEquals("resolved", entry.outcome());
        assertEquals(0.85, entry.confidence(), 0.0001);
    }

    @Test
    void constructsWithNullSolutionAndOutcome() {
        var entry = new CbrCaseEntry("server timeout", null, null, 0.5);
        assertNull(entry.solution());
        assertNull(entry.outcome());
    }

    @Test
    void rejectsNullProblem() {
        assertThrows(NullPointerException.class,
                () -> new CbrCaseEntry(null, "solution", "outcome", 0.5));
    }

    @Test
    void rejectsBlankProblem() {
        assertThrows(IllegalArgumentException.class,
                () -> new CbrCaseEntry("  ", "solution", "outcome", 0.5));
    }

    @Test
    void rejectsConfidenceOutOfRange() {
        assertThrows(IllegalArgumentException.class,
                () -> new CbrCaseEntry("problem", "solution", "outcome", -0.1));
        assertThrows(IllegalArgumentException.class,
                () -> new CbrCaseEntry("problem", "solution", "outcome", 1.1));
    }

    @Test
    void acceptsBoundaryConfidence() {
        assertDoesNotThrow(() -> new CbrCaseEntry("p", "s", "o", 0.0));
        assertDoesNotThrow(() -> new CbrCaseEntry("p", "s", "o", 1.0));
    }

    @Test
    void toMemoryInputMapsProblemToText() {
        var entry = new CbrCaseEntry("server timeout", "add retry logic", "resolved", 0.85);
        MemoryInput input = entry.toMemoryInput(SUBJECT, TENANT);

        assertEquals("server timeout", input.text());
        assertEquals(SUBJECT, input.subject());
        assertEquals(TENANT, input.tenantId());
        assertEquals(CbrCaseEntry.DOMAIN, input.domain());
    }

    @Test
    void toMemoryInputStoresAttributeKeys() {
        var entry = new CbrCaseEntry("server timeout", "add retry logic", "resolved", 0.85);
        MemoryInput input = entry.toMemoryInput(SUBJECT, TENANT);

        assertEquals("add retry logic", input.attributes().get(MemoryAttributeKeys.SOLUTION));
        assertEquals("resolved", input.attributes().get(MemoryAttributeKeys.OUTCOME));
        assertEquals("0.8500", input.attributes().get(MemoryAttributeKeys.CONFIDENCE));
    }

    @Test
    void toMemoryInputOmitsNullSolutionAndOutcome() {
        var entry = new CbrCaseEntry("problem", null, null, 0.5);
        MemoryInput input = entry.toMemoryInput(SUBJECT, TENANT);

        assertFalse(input.attributes().containsKey(MemoryAttributeKeys.SOLUTION));
        assertFalse(input.attributes().containsKey(MemoryAttributeKeys.OUTCOME));
        assertTrue(input.attributes().containsKey(MemoryAttributeKeys.CONFIDENCE));
    }

    @Test
    void fromMemoryExtractsAllFields() {
        var memory = new Memory("m1", Subject.of("agent", "a1"),
                new MemoryDomain("solution"), TENANT, null, "server timeout",
                Map.of(MemoryAttributeKeys.SOLUTION, "add retry logic",
                       MemoryAttributeKeys.OUTCOME, "resolved",
                       MemoryAttributeKeys.CONFIDENCE, "0.8500"),
                Instant.now(), null, null, null, null, null, null);

        CbrCaseEntry entry = CbrCaseEntry.from(memory);
        assertEquals("server timeout", entry.problem());
        assertEquals("add retry logic", entry.solution());
        assertEquals("resolved", entry.outcome());
        assertEquals(0.85, entry.confidence(), 0.0001);
    }

    @Test
    void fromMemoryDefaultsMissingAttributes() {
        var memory = new Memory("m1", Subject.of("agent", "a1"),
                new MemoryDomain("solution"), TENANT, null, "a problem",
                Map.of(), Instant.now(), null, null, null, null, null, null);

        CbrCaseEntry entry = CbrCaseEntry.from(memory);
        assertEquals("a problem", entry.problem());
        assertNull(entry.solution());
        assertNull(entry.outcome());
        assertEquals(0.0, entry.confidence(), 0.0001);
    }

    @Test
    void roundTrip() {
        var original = new CbrCaseEntry("timeout on login", "increase pool size", "fixed", 0.92);
        MemoryInput input = original.toMemoryInput(SUBJECT, TENANT);

        var memory = new Memory("m1", input.subject(), input.domain(), input.tenantId(),
                input.caseId(), input.text(), input.attributes(), Instant.now(),
                input.confidence(), input.pleasure(), input.arousal(), input.dominance(),
                input.principalId(), input.sharedWith());

        CbrCaseEntry restored = CbrCaseEntry.from(memory);
        assertEquals(original, restored);
    }

    @Test
    void domainIsSolution() {
        assertEquals(new MemoryDomain("solution"), CbrCaseEntry.DOMAIN);
    }
}

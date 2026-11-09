package io.casehub.neocortex.mindmap;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class ContinuumPositionTest {

    @Test
    void enumHasFourPositions() {
        assertEquals(4, ContinuumPosition.values().length);
        assertNotNull(ContinuumPosition.HEALTHY);
        assertNotNull(ContinuumPosition.FIXATION);
        assertNotNull(ContinuumPosition.OBSESSION);
        assertNotNull(ContinuumPosition.RUMINATION);
    }

    @Test
    void assessmentRecordRoundTrip() {
        var assessment = new RecurringThoughtAssessment(
                "node-1", 0.5, 0.3, 0.2, 0.1, 0.4, 0.6, 0.0, 0.3, ContinuumPosition.FIXATION);
        assertEquals("node-1", assessment.topicNodeId());
        assertEquals(0.5, assessment.thoughtRecurrence());
        assertEquals(ContinuumPosition.FIXATION, assessment.position());
    }

    @Test
    void signalCategoryIncludesThoughtRecurrence() {
        assertNotNull(SignalCategory.THOUGHT_RECURRENCE);
    }

    @Test
    void ordinalReflectsSeverity() {
        assertEquals(0, ContinuumPosition.HEALTHY.ordinal());
        assertEquals(1, ContinuumPosition.FIXATION.ordinal());
        assertEquals(2, ContinuumPosition.OBSESSION.ordinal());
        assertEquals(3, ContinuumPosition.RUMINATION.ordinal());
    }
}

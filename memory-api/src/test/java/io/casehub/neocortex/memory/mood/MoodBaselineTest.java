package io.casehub.neocortex.memory.mood;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class MoodBaselineTest {

    @Test
    void acceptsValidBaseline() {
        var baseline = new MoodBaseline(0.5, -0.3, 0.0);
        assertEquals(0.5, baseline.pleasure());
        assertEquals(-0.3, baseline.arousal());
        assertEquals(0.0, baseline.dominance());
    }

    @Test
    void acceptsBoundaryValues() {
        var baseline = new MoodBaseline(1.0, -1.0, 1.0);
        assertEquals(1.0, baseline.pleasure());
        assertEquals(-1.0, baseline.arousal());
    }

    @Test
    void rejectsPleasureOutOfBounds() {
        assertThrows(IllegalArgumentException.class, () -> new MoodBaseline(1.1, 0.0, 0.0));
        assertThrows(IllegalArgumentException.class, () -> new MoodBaseline(-1.1, 0.0, 0.0));
    }

    @Test
    void rejectsArousalOutOfBounds() {
        assertThrows(IllegalArgumentException.class, () -> new MoodBaseline(0.0, 1.1, 0.0));
    }

    @Test
    void rejectsDominanceOutOfBounds() {
        assertThrows(IllegalArgumentException.class, () -> new MoodBaseline(0.0, 0.0, -1.1));
    }
}

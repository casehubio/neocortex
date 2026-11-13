package io.casehub.neocortex.cognition.journal;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class EventRefTest {

    @Test
    void initFactoryAndToString() {
        EventRef ref = EventRef.init();
        assertInstanceOf(EventRef.Init.class, ref);
        assertEquals("init", ref.toString());
    }

    @Test
    void tickFactoryAndToString() {
        EventRef ref = EventRef.tick(47);
        assertInstanceOf(EventRef.Tick.class, ref);
        assertEquals("tick:47", ref.toString());
        assertEquals(47, ((EventRef.Tick) ref).number());
    }

    @Test
    void eventFactoryAndToString() {
        EventRef ref = EventRef.event("abc123");
        assertInstanceOf(EventRef.Event.class, ref);
        assertEquals("event:abc123", ref.toString());
    }

    @Test
    void consolidationFactoryAndToString() {
        EventRef ref = EventRef.consolidation(EventRef.tick(120));
        assertInstanceOf(EventRef.Consolidation.class, ref);
        assertEquals("consolidation:tick:120", ref.toString());
    }

    @Test
    void parseRoundTrip() {
        assertEquals(EventRef.init(), EventRef.parse("init"));
        assertEquals(EventRef.tick(47), EventRef.parse("tick:47"));
        assertEquals(EventRef.event("abc"), EventRef.parse("event:abc"));
        assertEquals(EventRef.consolidation(EventRef.tick(120)),
                EventRef.parse("consolidation:tick:120"));
    }

    @Test
    void parseInvalidThrows() {
        assertThrows(IllegalArgumentException.class, () -> EventRef.parse("garbage"));
    }

    @Test
    void equality() {
        assertEquals(EventRef.tick(1), EventRef.tick(1));
        assertNotEquals(EventRef.tick(1), EventRef.tick(2));
        assertNotEquals(EventRef.tick(1), EventRef.init());
    }
}

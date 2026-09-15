package io.casehub.neocortex.memory.experience;

import io.casehub.neocortex.cognitive.Confidence;
import io.casehub.neocortex.memory.Memory;
import io.casehub.neocortex.memory.MemoryDomain;
import io.casehub.neocortex.memory.Subject;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class ScoreableContentTest {

    @Test
    void constructionWithAllFields() {
        var content = new ScoreableContent("hello", Map.of("k", "v"), Instant.EPOCH);
        assertEquals("hello", content.text());
        assertEquals(Map.of("k", "v"), content.metadata());
        assertEquals(Instant.EPOCH, content.timestamp());
    }

    @Test
    void nullTextThrows() {
        assertThrows(NullPointerException.class,
            () -> new ScoreableContent(null, Map.of(), Instant.EPOCH));
    }

    @Test
    void nullMetadataDefaultsToEmpty() {
        var content = new ScoreableContent("text", null, Instant.EPOCH);
        assertTrue(content.metadata().isEmpty());
    }

    @Test
    void metadataIsDefensivelyCopied() {
        var mutable = new HashMap<String, String>();
        mutable.put("a", "b");
        var content = new ScoreableContent("text", mutable, Instant.EPOCH);
        mutable.put("c", "d");
        assertFalse(content.metadata().containsKey("c"));
    }

    @Test
    void fromMemory() {
        var memory = new Memory(
            "mem-1",
            Subject.of("agent", "entity-1"),
            new MemoryDomain("test"),
            "tenant-1", "case-1", "event text",
            Map.of("event-type", "observation"),
            Instant.parse("2026-01-01T00:00:00Z"),
            null, null, null, null, null, null);

        var content = ScoreableContent.fromMemory(memory);
        assertEquals("event text", content.text());
        assertEquals("observation", content.metadata().get("event-type"));
        assertEquals(Instant.parse("2026-01-01T00:00:00Z"), content.timestamp());
    }
}

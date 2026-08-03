package io.casehub.neocortex.memory.reflection;

import io.casehub.neocortex.memory.MemoryOrder;
import io.casehub.neocortex.memory.MemoryQuery;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class ReflectionQueryTest {

    @Test
    void forAgentSetsDefaults() {
        MemoryQuery q = ReflectionQuery.forAgent("a1", "t1");
        assertEquals(List.of("a1"), q.entityIds());
        assertEquals(ReflectionEvents.DOMAIN, q.domain());
        assertEquals(30, q.limit());
        assertEquals(MemoryOrder.CHRONOLOGICAL, q.order());
    }

    @Test
    void searchUsesRelevance() {
        MemoryQuery q = ReflectionQuery.search("a1", "t1", "cooperation patterns");
        assertEquals("cooperation patterns", q.question());
        assertEquals(MemoryOrder.RELEVANCE, q.order());
        assertEquals(15, q.limit());
    }

    @Test
    void salientUsesSalience() {
        MemoryQuery q = ReflectionQuery.salient("a1", "t1");
        assertEquals(MemoryOrder.SALIENCE, q.order());
        assertEquals(15, q.limit());
    }

    @Test
    void withMethodsChain() {
        MemoryQuery q = ReflectionQuery.forAgent("a1", "t1")
            .withLimit(5)
            .withCaseId("c1");
        assertEquals(5, q.limit());
        assertEquals("c1", q.caseId());
    }
}

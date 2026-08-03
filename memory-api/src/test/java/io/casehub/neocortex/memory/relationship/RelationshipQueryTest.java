package io.casehub.neocortex.memory.relationship;

import io.casehub.neocortex.memory.MemoryOrder;
import io.casehub.neocortex.memory.MemoryQuery;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class RelationshipQueryTest {

    @Test
    void forPairUsesMultiEntity() {
        MemoryQuery q = RelationshipQuery.forPair("a1", "b1", "t1");
        assertEquals(List.of("a1", "b1"), q.entityIds());
        assertEquals(RelationshipEvents.DOMAIN, q.domain());
        assertEquals("t1", q.tenantId());
        assertEquals(50, q.limit());
        assertEquals(MemoryOrder.CHRONOLOGICAL, q.order());
    }

    @Test
    void forAgentUsesSingleEntity() {
        MemoryQuery q = RelationshipQuery.forAgent("a1", "t1");
        assertEquals(List.of("a1"), q.entityIds());
        assertEquals(RelationshipEvents.DOMAIN, q.domain());
        assertEquals(50, q.limit());
    }

    @Test
    void searchUsesRelevanceOrder() {
        MemoryQuery q = RelationshipQuery.search("a1", "t1", "cooperation on reviews");
        assertEquals("cooperation on reviews", q.question());
        assertEquals(MemoryOrder.RELEVANCE, q.order());
        assertEquals(20, q.limit());
    }

    @Test
    void withMethodsChain() {
        MemoryQuery q = RelationshipQuery.forAgent("a1", "t1")
            .withLimit(10)
            .withCaseId("c1");
        assertEquals(10, q.limit());
        assertEquals("c1", q.caseId());
        assertEquals(RelationshipEvents.DOMAIN, q.domain());
    }
}

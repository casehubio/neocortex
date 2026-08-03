package io.casehub.neocortex.memory.experience;

import io.casehub.neocortex.memory.MemoryOrder;
import io.casehub.neocortex.memory.MemoryQuery;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class ExperienceQueryTest {

    @Test
    void forAgentSetsCorrectDefaults() {
        MemoryQuery q = ExperienceQuery.forAgent("agent-1", "tenant-1");
        assertEquals(List.of("agent-1"), q.entityIds());
        assertEquals(ExperienceEvents.DOMAIN, q.domain());
        assertEquals("tenant-1", q.tenantId());
        assertNull(q.caseId());
        assertNull(q.question());
        assertEquals(50, q.limit());
        assertNull(q.since());
        assertEquals(MemoryOrder.CHRONOLOGICAL, q.order());
    }

    @Test
    void forAgentInCaseSetsCase() {
        MemoryQuery q = ExperienceQuery.forAgentInCase("a1", "t1", "case-1");
        assertEquals("case-1", q.caseId());
        assertEquals(ExperienceEvents.DOMAIN, q.domain());
        assertEquals(MemoryOrder.CHRONOLOGICAL, q.order());
    }

    @Test
    void forAgentsAcceptsMultipleAgents() {
        MemoryQuery q = ExperienceQuery.forAgents(List.of("a1", "a2", "a3"), "t1");
        assertEquals(List.of("a1", "a2", "a3"), q.entityIds());
        assertEquals(ExperienceEvents.DOMAIN, q.domain());
        assertEquals(50, q.limit());
    }

    @Test
    void searchUsesRelevanceOrder() {
        MemoryQuery q = ExperienceQuery.search("a1", "t1", "merge conflict");
        assertEquals("merge conflict", q.question());
        assertEquals(MemoryOrder.RELEVANCE, q.order());
        assertEquals(20, q.limit());
    }

    @Test
    void salientUsesSalienceOrder() {
        MemoryQuery q = ExperienceQuery.salient("a1", "t1");
        assertEquals(MemoryOrder.SALIENCE, q.order());
        assertEquals(20, q.limit());
        assertNull(q.question());
    }

    @Test
    void withMethodsChainCorrectly() {
        MemoryQuery q = ExperienceQuery.forAgent("a1", "t1")
            .withLimit(10)
            .withCaseId("c1");
        assertEquals(10, q.limit());
        assertEquals("c1", q.caseId());
        assertEquals(ExperienceEvents.DOMAIN, q.domain());
    }
}

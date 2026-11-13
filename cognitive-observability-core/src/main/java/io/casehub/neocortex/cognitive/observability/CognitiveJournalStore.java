package io.casehub.neocortex.cognitive.observability;

import io.casehub.neocortex.cognition.journal.CognitiveEntry;
import io.casehub.neocortex.cognition.journal.EventRef;

import java.time.Instant;
import java.util.List;

public interface CognitiveJournalStore {

    void persist(CognitiveEntry entry);

    void persistAll(List<CognitiveEntry> entries);

    List<CognitiveEntry> findByAgent(String tenantId, String agentId);

    List<CognitiveEntry> findByEventRef(String tenantId, String agentId, EventRef eventRef);

    List<CognitiveEntry> findByTickRange(String tenantId, String agentId, int fromTick, int toTick);

    <T extends CognitiveEntry> List<T> findByType(String tenantId, String agentId, Class<T> type);

    List<CognitiveEntry> findByTimeWindow(String tenantId, String agentId, Instant from, Instant to);
}

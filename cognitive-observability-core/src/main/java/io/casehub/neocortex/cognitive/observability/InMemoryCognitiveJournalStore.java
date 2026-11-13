package io.casehub.neocortex.cognitive.observability;

import io.casehub.neocortex.cognition.journal.CognitiveEntry;
import io.casehub.neocortex.cognition.journal.EventRef;

import java.time.Instant;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

public class InMemoryCognitiveJournalStore implements CognitiveJournalStore {

    private final List<CognitiveEntry> entries = new CopyOnWriteArrayList<>();

    @Override
    public void persist(CognitiveEntry entry) { entries.add(entry); }

    @Override
    public void persistAll(List<CognitiveEntry> batch) { entries.addAll(batch); }

    @Override
    public List<CognitiveEntry> findByAgent(String tenantId, String agentId) {
        return entries.stream()
                .filter(e -> tenantId.equals(e.tenantId()) && agentId.equals(e.agentId()))
                .toList();
    }

    @Override
    public List<CognitiveEntry> findByEventRef(String tenantId, String agentId, EventRef eventRef) {
        return entries.stream()
                .filter(e -> tenantId.equals(e.tenantId()) && agentId.equals(e.agentId())
                             && eventRef.equals(e.eventRef()))
                .toList();
    }

    @Override
    public List<CognitiveEntry> findByTickRange(String tenantId, String agentId, int fromTick, int toTick) {
        return entries.stream()
                .filter(e -> tenantId.equals(e.tenantId()) && agentId.equals(e.agentId()))
                .filter(e -> e.eventRef() instanceof EventRef.Tick t
                             && t.number() >= fromTick && t.number() <= toTick)
                .toList();
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T extends CognitiveEntry> List<T> findByType(String tenantId, String agentId, Class<T> type) {
        return entries.stream()
                .filter(e -> tenantId.equals(e.tenantId()) && agentId.equals(e.agentId()))
                .filter(type::isInstance)
                .map(type::cast)
                .toList();
    }

    @Override
    public List<CognitiveEntry> findByTimeWindow(String tenantId, String agentId, Instant from, Instant to) {
        return entries.stream()
                .filter(e -> tenantId.equals(e.tenantId()) && agentId.equals(e.agentId()))
                .filter(e -> !e.timestamp().isBefore(from) && !e.timestamp().isAfter(to))
                .toList();
    }
}

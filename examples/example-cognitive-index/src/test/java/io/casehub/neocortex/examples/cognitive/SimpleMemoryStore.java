package io.casehub.neocortex.examples.cognitive;

import io.casehub.neocortex.cognitive.Confidence;
import io.casehub.neocortex.cognitive.ConfidenceOrigin;
import io.casehub.neocortex.memory.CaseMemoryStore;
import io.casehub.neocortex.memory.EraseRequest;
import io.casehub.neocortex.memory.Memory;
import io.casehub.neocortex.memory.MemoryInput;
import io.casehub.neocortex.memory.MemoryQuery;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicLong;

class SimpleMemoryStore implements CaseMemoryStore {

    private static final Confidence DEFAULT_CONF =
        new Confidence(ConfidenceOrigin.STATED, 0.9, Instant.now());

    private final List<Memory> memories = new CopyOnWriteArrayList<>();
    private final AtomicLong tick = new AtomicLong();
    private Instant baseTime = Instant.parse("2026-06-01T00:00:00Z");

    void setBaseTime(Instant base) { this.baseTime = base; }

    @Override
    public String store(MemoryInput input) {
        String id = UUID.randomUUID().toString();
        Instant ts = baseTime.plusSeconds(tick.getAndIncrement() * 28800);
        Confidence conf = input.confidence() != null ? input.confidence() : DEFAULT_CONF;
        memories.add(new Memory(id, input.subject(), input.domain(), input.tenantId(),
            input.caseId(), input.text(), input.attributes(), ts,
            conf, input.pleasure(), input.arousal(), input.dominance(),
            input.principalId(), null));
        return id;
    }

    @Override
    public List<Memory> query(MemoryQuery query) {
        return memories.stream()
            .filter(m -> query.entityIds().contains(m.entityId()))
            .filter(m -> m.domain().equals(query.domain()))
            .filter(m -> m.tenantId().equals(query.tenantId()))
            .filter(m -> query.since() == null || !m.createdAt().isBefore(query.since()))
            .filter(m -> query.callerPrincipalId() == null
                || m.principalId() == null
                || m.principalId().equals(query.callerPrincipalId()))
            .limit(query.limit())
            .toList();
    }

    @Override
    public int erase(EraseRequest request) {
        return 0;
    }
}

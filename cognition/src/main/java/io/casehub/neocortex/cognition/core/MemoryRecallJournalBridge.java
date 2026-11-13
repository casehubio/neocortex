package io.casehub.neocortex.cognition.core;

import io.casehub.neocortex.cognition.journal.CognitiveEntry;
import io.casehub.neocortex.cognition.journal.CognitiveJournal;
import io.casehub.neocortex.cognition.journal.EventRef;
import io.casehub.neocortex.cognitive.observability.MemoryRecallObserver;

import java.time.Instant;

public class MemoryRecallJournalBridge implements MemoryRecallObserver {

    private final CognitiveJournal journal;

    public MemoryRecallJournalBridge(CognitiveJournal journal) {
        this.journal = journal;
    }

    @Override
    public void onRecall(String agentId, String tenantId, int resultCount) {
        journal.record(new CognitiveEntry.MemoryRecalled(
                tenantId, agentId, EventRef.event("recall"), Instant.now(),
                "memory", resultCount));
    }
}

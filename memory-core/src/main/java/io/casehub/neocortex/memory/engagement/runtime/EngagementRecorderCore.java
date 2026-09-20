package io.casehub.neocortex.memory.engagement.runtime;

import io.casehub.neocortex.memory.CaseMemoryStore;
import io.casehub.neocortex.memory.MemoryInput;
import io.casehub.neocortex.memory.engagement.EngagementEvent;
import io.casehub.neocortex.memory.engagement.EngagementEvents;
import io.casehub.neocortex.memory.engagement.EngagementRecorded;
import io.casehub.neocortex.memory.engagement.EngagementStoreFailure;
import io.casehub.neocortex.memory.engagement.EngagementStoreResult;
import io.casehub.neocortex.memory.runtime.EventRecorderCore;

import java.util.List;
import java.util.function.Consumer;

public class EngagementRecorderCore
        extends EventRecorderCore<EngagementEvent, EngagementRecorded, EngagementStoreFailure, EngagementStoreResult> {

    protected EngagementRecorderCore() { super(); }

    public EngagementRecorderCore(CaseMemoryStore store, Consumer<EngagementRecorded> recorded) {
        super(store, recorded);
    }

    @Override protected MemoryInput toInput(EngagementEvent event) { return EngagementEvents.toMemoryInput(event); }
    @Override protected EngagementRecorded toRecorded(EngagementEvent event, String memoryId) { return new EngagementRecorded(event, memoryId); }
    @Override protected EngagementStoreFailure toFailure(int inputIndex, EngagementEvent event, RuntimeException cause) { return new EngagementStoreFailure(inputIndex, event, cause); }
    @Override protected EngagementStoreResult toResult(List<String> stored, List<EngagementStoreFailure> failures) { return new EngagementStoreResult(stored, failures); }
    @Override protected EngagementStoreResult emptyResult() { return EngagementStoreResult.empty(); }
}

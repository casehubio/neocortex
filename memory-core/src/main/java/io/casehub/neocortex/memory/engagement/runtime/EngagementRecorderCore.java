package io.casehub.neocortex.memory.engagement.runtime;

import io.casehub.neocortex.memory.CaseMemoryStore;
import io.casehub.neocortex.memory.StoreAllResult;
import io.casehub.neocortex.memory.engagement.EngagementEvent;
import io.casehub.neocortex.memory.engagement.EngagementEvents;
import io.casehub.neocortex.memory.engagement.EngagementRecorded;
import io.casehub.neocortex.memory.engagement.EngagementStoreFailure;
import io.casehub.neocortex.memory.engagement.EngagementStoreResult;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.function.Consumer;

public class EngagementRecorderCore {

    private final CaseMemoryStore store;
    private final Consumer<EngagementRecorded> recorded;

    public EngagementRecorderCore(CaseMemoryStore store, Consumer<EngagementRecorded> recorded) {
        this.store = store;
        this.recorded = recorded;
    }

    public String record(EngagementEvent event) {
        var input = EngagementEvents.toMemoryInput(event);
        var memoryId = store.store(input);
        recorded.accept(new EngagementRecorded(event, memoryId));
        return memoryId;
    }

    public EngagementStoreResult recordAll(List<EngagementEvent> events) {
        if (events.isEmpty()) return EngagementStoreResult.empty();

        var inputs = events.stream()
            .map(EngagementEvents::toMemoryInput)
            .toList();

        StoreAllResult storeResult = store.storeAll(inputs);

        var failedIndices = new HashSet<Integer>();
        var failures = new ArrayList<EngagementStoreFailure>();
        for (var sf : storeResult.failures()) {
            failedIndices.add(sf.inputIndex());
            failures.add(new EngagementStoreFailure(sf.inputIndex(),
                events.get(sf.inputIndex()), sf.cause()));
        }

        int storedIdx = 0;
        for (int i = 0; i < events.size(); i++) {
            if (!failedIndices.contains(i)) {
                recorded.accept(new EngagementRecorded(events.get(i),
                    storeResult.stored().get(storedIdx)));
                storedIdx++;
            }
        }

        return new EngagementStoreResult(storeResult.stored(), failures);
    }
}

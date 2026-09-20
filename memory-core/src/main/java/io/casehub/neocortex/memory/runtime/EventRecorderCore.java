package io.casehub.neocortex.memory.runtime;

import io.casehub.neocortex.memory.CaseMemoryStore;
import io.casehub.neocortex.memory.MemoryInput;
import io.casehub.neocortex.memory.StoreAllResult;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.function.Consumer;

public abstract class EventRecorderCore<E, R, F, S> {

    private final CaseMemoryStore store;
    private final Consumer<R> recorded;

    protected EventRecorderCore() { this.store = null; this.recorded = null; }

    protected EventRecorderCore(CaseMemoryStore store, Consumer<R> recorded) {
        this.store = store;
        this.recorded = recorded;
    }

    protected abstract MemoryInput toInput(E event);
    protected abstract R toRecorded(E event, String memoryId);
    protected abstract F toFailure(int inputIndex, E event, RuntimeException cause);
    protected abstract S toResult(List<String> stored, List<F> failures);
    protected abstract S emptyResult();

    public String record(E event) {
        var input = toInput(event);
        var memoryId = store.store(input);
        recorded.accept(toRecorded(event, memoryId));
        return memoryId;
    }

    public S recordAll(List<E> events) {
        if (events.isEmpty()) return emptyResult();

        var inputs = events.stream()
                           .map(this::toInput)
                           .toList();

        StoreAllResult storeResult = store.storeAll(inputs);

        var failedIndices = new HashSet<Integer>();
        var failures = new ArrayList<F>();
        for (var sf : storeResult.failures()) {
            failedIndices.add(sf.inputIndex());
            failures.add(toFailure(sf.inputIndex(), events.get(sf.inputIndex()), sf.cause()));
        }

        int storedIdx = 0;
        for (int i = 0; i < events.size(); i++) {
            if (!failedIndices.contains(i)) {
                recorded.accept(toRecorded(events.get(i), storeResult.stored().get(storedIdx)));
                storedIdx++;
            }
        }

        return toResult(storeResult.stored(), failures);
    }
}

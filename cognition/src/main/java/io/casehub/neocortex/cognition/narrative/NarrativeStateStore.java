package io.casehub.neocortex.cognition.narrative;

import io.casehub.neocortex.summarisation.StateStore;
import org.jspecify.annotations.Nullable;

public class NarrativeStateStore implements StateStore<NarrativeState> {

    private final NarrativeMemory memory;

    public NarrativeStateStore(NarrativeMemory memory) {
        this.memory = memory;
    }

    @Override
    public @Nullable NarrativeState load(String partitionKey) {
        var parts = partitionKey.split(":", 2);
        if (parts.length == 2) {
            return memory.load(parts[0], parts[1]);
        }
        return memory.load(partitionKey, partitionKey);
    }

    @Override
    public void store(String partitionKey, NarrativeState state) {
        memory.store(state);
    }
}

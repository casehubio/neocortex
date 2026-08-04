package io.casehub.neocortex.memory.experience.runtime;

import io.casehub.neocortex.memory.CaseMemoryStore;
import io.casehub.neocortex.memory.StoreAllResult;
import io.casehub.neocortex.memory.experience.ExperienceEvent;
import io.casehub.neocortex.memory.experience.ExperienceRecorder;
import io.casehub.neocortex.memory.experience.ExperienceEvents;
import io.casehub.neocortex.memory.experience.ExperienceRecorded;
import io.casehub.neocortex.memory.experience.ExperienceStoreFailure;
import io.casehub.neocortex.memory.experience.ExperienceStoreResult;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Event;
import jakarta.inject.Inject;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;

@ApplicationScoped
public class ExperienceStream implements ExperienceRecorder {

    private final CaseMemoryStore           store;
    private final Event<ExperienceRecorded> recorded;

    @Inject
    ExperienceStream(CaseMemoryStore store, Event<ExperienceRecorded> recorded) {
        this.store    = store;
        this.recorded = recorded;
    }

    @Override
    public String record(ExperienceEvent event) {
        var input    = ExperienceEvents.toMemoryInput(event);
        var memoryId = store.store(input);
        recorded.fire(new ExperienceRecorded(event, memoryId));
        return memoryId;
    }

    @Override
    public ExperienceStoreResult recordAll(List<ExperienceEvent> events) {
        if (events.isEmpty()) {return ExperienceStoreResult.empty();}

        var inputs = events.stream()
                           .map(ExperienceEvents::toMemoryInput)
                           .toList();

        StoreAllResult storeResult = store.storeAll(inputs);

        var failedIndices = new HashSet<Integer>();
        var failures      = new ArrayList<ExperienceStoreFailure>();
        for (var sf : storeResult.failures()) {
            failedIndices.add(sf.inputIndex());
            failures.add(new ExperienceStoreFailure(sf.inputIndex(), events.get(sf.inputIndex()), sf.cause()));
        }

        int storedIdx = 0;
        for (int i = 0; i < events.size(); i++) {
            if (!failedIndices.contains(i)) {
                recorded.fire(new ExperienceRecorded(events.get(i), storeResult.stored().get(storedIdx)));
                storedIdx++;
            }
        }

        return new ExperienceStoreResult(storeResult.stored(), failures);
    }
}

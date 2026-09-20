package io.casehub.neocortex.memory.experience.runtime;

import io.casehub.neocortex.memory.CaseMemoryStore;
import io.casehub.neocortex.memory.MemoryInput;
import io.casehub.neocortex.memory.experience.ExperienceEvent;
import io.casehub.neocortex.memory.experience.ExperienceEvents;
import io.casehub.neocortex.memory.experience.ExperienceRecorded;
import io.casehub.neocortex.memory.experience.ExperienceRecorder;
import io.casehub.neocortex.memory.experience.ExperienceStoreFailure;
import io.casehub.neocortex.memory.experience.ExperienceStoreResult;
import io.casehub.neocortex.memory.runtime.EventRecorderCore;

import java.util.List;
import java.util.function.Consumer;

public class ExperienceRecorderCore
        extends EventRecorderCore<ExperienceEvent, ExperienceRecorded, ExperienceStoreFailure, ExperienceStoreResult>
        implements ExperienceRecorder {

    public ExperienceRecorderCore(CaseMemoryStore store, Consumer<ExperienceRecorded> recorded) {
        super(store, recorded);
    }

    @Override protected MemoryInput toInput(ExperienceEvent event) { return ExperienceEvents.toMemoryInput(event); }
    @Override protected ExperienceRecorded toRecorded(ExperienceEvent event, String memoryId) { return new ExperienceRecorded(event, memoryId); }
    @Override protected ExperienceStoreFailure toFailure(int inputIndex, ExperienceEvent event, RuntimeException cause) { return new ExperienceStoreFailure(inputIndex, event, cause); }
    @Override protected ExperienceStoreResult toResult(List<String> stored, List<ExperienceStoreFailure> failures) { return new ExperienceStoreResult(stored, failures); }
    @Override protected ExperienceStoreResult emptyResult() { return ExperienceStoreResult.empty(); }
}

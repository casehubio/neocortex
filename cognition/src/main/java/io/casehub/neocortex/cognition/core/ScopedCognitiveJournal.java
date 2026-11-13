package io.casehub.neocortex.cognition.core;

import io.casehub.neocortex.cognition.journal.CognitiveEntry;
import io.casehub.neocortex.cognition.journal.CognitiveJournal;
import io.casehub.neocortex.cognition.journal.CognitiveTrace;
import io.casehub.neocortex.cognitive.observability.CognitiveJournalStore;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.util.function.Predicate;

@ApplicationScoped
public class ScopedCognitiveJournal implements CognitiveJournal {

    private final CognitiveJournalStore store;
    private final ThreadLocal<CognitiveTrace> currentTrace = new ThreadLocal<>();

    @Inject
    public ScopedCognitiveJournal(CognitiveJournalStore store) {
        this.store = store;
    }

    public void beginScope(Predicate<CognitiveEntry> filter) {
        currentTrace.set(new CognitiveTrace(filter));
    }

    @Override
    public void record(CognitiveEntry entry) {
        CognitiveTrace trace = currentTrace.get();
        if (trace != null) trace.record(entry);
        store.persist(entry);
    }

    public CognitiveTrace endScope() {
        CognitiveTrace trace = currentTrace.get();
        currentTrace.remove();
        return trace;
    }
}

package io.casehub.neocortex.cognition.journal;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.Predicate;

public class CognitiveTrace implements CognitiveJournal {

    private final List<CognitiveEntry> entries = new ArrayList<>();
    private final Predicate<CognitiveEntry> filter;

    public CognitiveTrace() { this(e -> true); }

    public CognitiveTrace(Predicate<CognitiveEntry> filter) {
        this.filter = filter;
    }

    @Override
    public void record(CognitiveEntry entry) {
        if (filter.test(entry)) entries.add(entry);
    }

    public List<CognitiveEntry> entries() {
        return Collections.unmodifiableList(entries);
    }

    public List<CognitiveEntry> entriesForEvent(EventRef eventRef) {
        return entries.stream()
                .filter(e -> eventRef.equals(e.eventRef()))
                .toList();
    }

    @SuppressWarnings("unchecked")
    public <T extends CognitiveEntry> List<T> entriesOfType(Class<T> type) {
        return entries.stream()
                .filter(type::isInstance)
                .map(type::cast)
                .toList();
    }

    public List<CognitiveEntry> entriesForAgent(String agentId) {
        return entries.stream()
                .filter(e -> agentId.equals(e.agentId()))
                .toList();
    }

    public void clear() { entries.clear(); }

    public boolean isEmpty() { return entries.isEmpty(); }

    public int size() { return entries.size(); }
}

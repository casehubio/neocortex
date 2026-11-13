package io.casehub.neocortex.cognition.journal;

public interface CognitiveJournal {

    CognitiveJournal NOOP = entry -> {};

    void record(CognitiveEntry entry);
}

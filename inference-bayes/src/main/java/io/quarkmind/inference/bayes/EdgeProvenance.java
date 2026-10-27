package io.quarkmind.inference.bayes;

public enum EdgeProvenance {
    LLM_PROPOSED,
    DATA_CONFIRMED,
    DATA_DISCOVERED,
    LLM_REJECTED_BY_DATA
}

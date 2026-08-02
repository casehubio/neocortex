package io.casehub.neocortex.memory;

/**
 * Controls how {@link CaseMemoryStore#query} ranks results.
 *
 * <p>Non-semantic adapters (in-memory, JPA without FTS) always use
 * {@link #CHRONOLOGICAL} regardless of this field.
 */
public enum MemoryOrder {

    /**
     * Results ordered by creation time, newest first.
     * All adapters honour this mode.
     */
    CHRONOLOGICAL,

    /**
     * Results ordered by relevance to {@link MemoryQuery#question()}.
     * Adapters with relevance ranking (JPA FTS via ts_rank, Mem0 vector search,
     * Graphiti temporal graph) honour this; others silently fall back to
     * {@link #CHRONOLOGICAL}.
     * If {@code question} is {@code null}, all adapters fall back to
     * {@link #CHRONOLOGICAL}.
     */
    RELEVANCE,

    /**
 * Results ordered by salience: recency × importance.
 * Non-semantic adapters compute salience from {@link Memory#createdAt()} and
 * {@link Memory#importance()} — null importance treated as 1.0.
 * Semantic adapters that already handle relevance fall back to
 * {@link #RELEVANCE} (salience is a non-semantic ranking strategy).
 */
    SALIENCE

    }

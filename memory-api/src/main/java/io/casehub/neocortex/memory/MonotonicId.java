package io.casehub.neocortex.memory;

import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Generates lexicographically sortable string IDs for memory entries.
 *
 * <p>Format: {@code {millis-13}-{seq-6}-{rand-8}} — e.g. {@code 1732456789012-000001-a3f7b2c8}.
 * The 13-digit millisecond prefix ensures chronological ordering. The 6-digit
 * sequence counter guarantees uniqueness within the same millisecond on a single JVM.
 * The 8-character random suffix provides cross-JVM uniqueness.
 *
 * <p>Replaces {@code UUID.randomUUID()} which is not monotonically ordered and
 * breaks cursor-based pagination ({@code WHERE memory_id > ?}).
 */
public final class MonotonicId {

    private static final AtomicLong SEQUENCE = new AtomicLong();

    private MonotonicId() {}

    public static String generate() {
        long now = System.currentTimeMillis();
        long seq = SEQUENCE.incrementAndGet() % 1_000_000;
        String rand = UUID.randomUUID().toString().substring(0, 8);
        return String.format("%013d-%06d-%s", now, seq, rand);
    }
}

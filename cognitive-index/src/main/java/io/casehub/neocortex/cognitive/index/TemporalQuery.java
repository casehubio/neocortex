package io.casehub.neocortex.cognitive.index;

import java.time.Instant;
import java.util.Collection;
import java.util.EnumSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Specifies what to query from the {@link TemporalIndex} — time range,
 * tenants, and which stores to include. Factory methods cover common
 * patterns: {@link #since}, {@link #window}, {@link #upcoming}.
 *
 * <p>{@code entityIds} provides entity context for the Memory store
 * (required by {@link io.casehub.neocortex.memory.MemoryQuery}).
 * When empty, the Memory store is silently skipped even if
 * {@link StoreKind#MEMORY} is in {@code sources}.
 *
 * <p>This is part of a stateless derived view — it carries query
 * parameters, not persistent state.
 */
public record TemporalQuery(
    Collection<String> tenantIds,
    Instant from,
    Instant to,
    int limit,
    Set<StoreKind> sources,
    Collection<String> entityIds,
    boolean upcoming
) {
    public enum StoreKind { MINDMAP, MEMORY, CBR }

    public TemporalQuery {
        Objects.requireNonNull(tenantIds, "tenantIds required");
        if (tenantIds.isEmpty()) throw new IllegalArgumentException("at least one tenantId required");
        if (limit <= 0) throw new IllegalArgumentException("limit must be positive");
        if (sources == null || sources.isEmpty()) {
            sources = EnumSet.allOf(StoreKind.class);
        }
        if (entityIds == null) {
            entityIds = List.of();
        }
        tenantIds = List.copyOf(tenantIds);
        entityIds = List.copyOf(entityIds);
    }

    public static TemporalQuery since(Collection<String> tenantIds, Instant from, int limit) {
        return new TemporalQuery(tenantIds, from, null, limit, EnumSet.allOf(StoreKind.class), List.of(), false);
    }

    public static TemporalQuery window(Collection<String> tenantIds, Instant from, Instant to, int limit) {
        return new TemporalQuery(tenantIds, from, to, limit, EnumSet.allOf(StoreKind.class), List.of(), false);
    }

    public static TemporalQuery upcoming(Collection<String> tenantIds, Instant now, int limit) {
        return new TemporalQuery(tenantIds, now, null, limit, EnumSet.of(StoreKind.MINDMAP), List.of(), true);
    }

    public TemporalQuery withSources(Set<StoreKind> sources) {
        return new TemporalQuery(tenantIds, from, to, limit, sources, entityIds, upcoming);
    }

    public TemporalQuery withEntityIds(Collection<String> entityIds) {
        return new TemporalQuery(tenantIds, from, to, limit, sources, entityIds, upcoming);
    }
}

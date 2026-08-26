package io.casehub.neocortex.memory.runtime;

import io.casehub.neocortex.memory.*;
import jakarta.enterprise.event.Event;
import jakarta.enterprise.event.NotificationOptions;
import jakarta.enterprise.util.TypeLiteral;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.lang.annotation.Annotation;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

import static org.assertj.core.api.Assertions.assertThat;

class ErasureNotificationCaseMemoryStoreTest {

    private static final Instant FIXED = Instant.parse("2026-08-26T12:00:00Z");
    private static final Clock CLOCK = Clock.fixed(FIXED, ZoneOffset.UTC);
    private static final MemoryDomain DOMAIN = new MemoryDomain("test");

    private StubStore stub;
    private List<MemoryEntityErased.ByRequest> byRequestEvents;
    private List<MemoryEntityErased.ByEntity> byEntityEvents;
    private List<MemoryEntityErased.CrossTenant> crossTenantEvents;
    private ErasureNotificationCaseMemoryStore decorator;

    @BeforeEach
    void setUp() {
        stub = new StubStore();
        byRequestEvents = new ArrayList<>();
        byEntityEvents = new ArrayList<>();
        crossTenantEvents = new ArrayList<>();
        decorator = new ErasureNotificationCaseMemoryStore(
                stub,
                capturingEvent(byRequestEvents),
                capturingEvent(byEntityEvents),
                capturingEvent(crossTenantEvents),
                CLOCK);
    }

    @Test
    void erase_firesEvent_whenCountPositive() {
        stub.eraseReturnValue = 3;
        var request = new EraseRequest("e-1", DOMAIN, "t-1", null);
        int result = decorator.erase(request);
        assertThat(result).isEqualTo(3);
        assertThat(byRequestEvents).hasSize(1);
        var event = byRequestEvents.get(0);
        assertThat(event.tenantId()).isEqualTo("t-1");
        assertThat(event.erasedCount()).isEqualTo(3);
        assertThat(event.entityId()).isEqualTo("e-1");
        assertThat(event.domain()).isEqualTo(DOMAIN);
        assertThat(event.erasedAt()).isEqualTo(FIXED);
    }

    @Test
    void erase_noEvent_whenCountZero() {
        stub.eraseReturnValue = 0;
        decorator.erase(new EraseRequest("e-1", DOMAIN, "t-1", null));
        assertThat(byRequestEvents).isEmpty();
    }

    @Test
    void eraseEntity_firesEvent_whenCountPositive() {
        stub.eraseEntityReturnValue = 5;
        int result = decorator.eraseEntity("e-1", "t-1");
        assertThat(result).isEqualTo(5);
        assertThat(byEntityEvents).hasSize(1);
        var event = byEntityEvents.get(0);
        assertThat(event.tenantId()).isEqualTo("t-1");
        assertThat(event.erasedCount()).isEqualTo(5);
        assertThat(event.entityId()).isEqualTo("e-1");
        assertThat(event.erasedAt()).isEqualTo(FIXED);
    }

    @Test
    void eraseEntity_noEvent_whenCountZero() {
        stub.eraseEntityReturnValue = 0;
        decorator.eraseEntity("e-1", "t-1");
        assertThat(byEntityEvents).isEmpty();
    }

    @Test
    void eraseEntityAcrossTenants_firesEvent_whenCountPositive() {
        stub.eraseAcrossTenantsReturnValue = 8;
        int result = decorator.eraseEntityAcrossTenants("e-1", Set.of("t-1", "t-2"));
        assertThat(result).isEqualTo(8);
        assertThat(crossTenantEvents).hasSize(1);
        var event = crossTenantEvents.get(0);
        assertThat(event.tenantId()).isNull();
        assertThat(event.erasedCount()).isEqualTo(8);
        assertThat(event.entityId()).isEqualTo("e-1");
        assertThat(event.tenantIds()).containsExactlyInAnyOrder("t-1", "t-2");
        assertThat(event.erasedAt()).isEqualTo(FIXED);
    }

    @Test
    void eraseEntityAcrossTenants_noEvent_whenCountZero() {
        stub.eraseAcrossTenantsReturnValue = 0;
        decorator.eraseEntityAcrossTenants("e-1", Set.of("t-1"));
        assertThat(crossTenantEvents).isEmpty();
    }

    @Test
    void store_doesNotFireEvent() {
        decorator.store(new MemoryInput("e-1", DOMAIN, "t-1", null, "hello", java.util.Map.of(), null));
        assertThat(byRequestEvents).isEmpty();
        assertThat(byEntityEvents).isEmpty();
        assertThat(crossTenantEvents).isEmpty();
    }

    @SuppressWarnings("unchecked")
    private static <T> Event<T> capturingEvent(List<T> captured) {
        return new Event<>() {
            @Override public void fire(T event) { captured.add(event); }
            @Override public <U extends T> CompletionStage<U> fireAsync(U event) { return CompletableFuture.completedFuture(event); }
            @Override public <U extends T> CompletionStage<U> fireAsync(U event, NotificationOptions options) { return CompletableFuture.completedFuture(event); }
            @Override public Event<T> select(Annotation... qualifiers) { return this; }
            @Override public <U extends T> Event<U> select(Class<U> subtype, Annotation... qualifiers) { return (Event<U>) this; }
            @Override public <U extends T> Event<U> select(TypeLiteral<U> subtype, Annotation... qualifiers) { return (Event<U>) this; }
        };
    }

    private static class StubStore implements CaseMemoryStore {
        int eraseReturnValue = 0;
        int eraseEntityReturnValue = 0;
        int eraseAcrossTenantsReturnValue = 0;

        @Override public String store(MemoryInput input) { return "m-1"; }
        @Override public List<Memory> query(MemoryQuery query) { return List.of(); }
        @Override public int erase(EraseRequest request) { return eraseReturnValue; }
        @Override public int eraseEntity(String entityId, String tenantId) { return eraseEntityReturnValue; }
        @Override public int eraseEntityAcrossTenants(String entityId, Set<String> tenantIds) { return eraseAcrossTenantsReturnValue; }
    }
}

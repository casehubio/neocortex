/*
 * Copyright 2026-Present The Case Hub Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.casehub.neocortex.cognitive.runtime;

import io.casehub.neocortex.cognitive.index.BriefingConfig;
import io.casehub.neocortex.cognitive.index.CognitiveDefaults;
import io.casehub.neocortex.cognitive.index.CognitiveDefaultsRegistry;
import io.casehub.neocortex.cognitive.index.CognitiveProfile;
import io.casehub.neocortex.cognitive.index.CognitiveProfilesReloaded;
import io.casehub.neocortex.memory.CaseMemoryStore;
import io.casehub.neocortex.memory.inmem.InMemoryMemoryStore;
import io.casehub.neocortex.mindmap.MindMapStore;
import io.casehub.neocortex.mindmap.inmem.InMemoryMindMapStore;
import io.casehub.platform.api.identity.CurrentPrincipal;
import jakarta.enterprise.inject.Instance;
import org.junit.jupiter.api.Test;

import java.lang.annotation.Annotation;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class CognitiveAvatarProducerTest {

    private static final String TENANT = "test-tenant";

    private static final CurrentPrincipal PRINCIPAL = new CurrentPrincipal() {
        @Override public String actorId() { return "test"; }
        @Override public Set<String> groups() { return Set.of(); }
        @Override public String tenancyId() { return TENANT; }
        @Override public boolean isCrossTenantAdmin() { return false; }
    };

    @Test
    void createsAvatarWithAllCapabilities() {
        var mindMap = new InMemoryMindMapStore();
        var memory = new InMemoryMemoryStore(PRINCIPAL);
        var profile = new CognitiveProfile(mindMap, memory);
        var briefing = new BriefingConfig("identity", "grounding", "echo");
        var defaults = CognitiveDefaults.empty("mentor").withBriefing(briefing);
        var registry = CognitiveDefaultsRegistry.forTesting(defaults);

        var producer = new CognitiveAvatarProducer(
            registry,
            resolvedInstance(mindMap),
            resolvedInstance(memory),
            resolvedInstance(profile));

        var avatar = producer.create("mentor", TENANT);
        assertNotNull(avatar);

        var ctx = new io.casehub.neocortex.cognition.prompt.CognitionRenderContext("mentor", TENANT, null);
        var blocks = avatar.contributeBlocks(ctx);
        assertFalse(blocks.isEmpty());
    }

    @Test
    void cachesAvatarByTenantAndAgent() {
        var registry = CognitiveDefaultsRegistry.forTesting(CognitiveDefaults.empty("agent-1"));
        var producer = new CognitiveAvatarProducer(
            registry, unresolvedInstance(), unresolvedInstance(), unresolvedInstance());

        var first = producer.create("agent-1", TENANT);
        var second = producer.create("agent-1", TENANT);
        assertSame(first, second);
    }

    @Test
    void differentTenantsGetDifferentInstances() {
        var registry = CognitiveDefaultsRegistry.forTesting(CognitiveDefaults.empty("agent-1"));
        var producer = new CognitiveAvatarProducer(
            registry, unresolvedInstance(), unresolvedInstance(), unresolvedInstance());

        var a = producer.create("agent-1", "tenant-a");
        var b = producer.create("agent-1", "tenant-b");
        assertNotSame(a, b);
    }

    @Test
    void clearsCacheOnProfileReload() {
        var registry = CognitiveDefaultsRegistry.forTesting(CognitiveDefaults.empty("agent-1"));
        var producer = new CognitiveAvatarProducer(
            registry, unresolvedInstance(), unresolvedInstance(), unresolvedInstance());

        var before = producer.create("agent-1", TENANT);
        producer.onProfileReload(new CognitiveProfilesReloaded(List.of()));
        var after = producer.create("agent-1", TENANT);
        assertNotSame(before, after);
    }

    @Test
    void gracefulDegradationWithoutStores() {
        var briefing = new BriefingConfig("identity", null, null);
        var defaults = CognitiveDefaults.empty("minimal").withBriefing(briefing);
        var registry = CognitiveDefaultsRegistry.forTesting(defaults);

        var producer = new CognitiveAvatarProducer(
            registry, unresolvedInstance(), unresolvedInstance(), unresolvedInstance());

        var avatar = producer.create("minimal", TENANT);
        assertNotNull(avatar);

        var ctx = new io.casehub.neocortex.cognition.prompt.CognitionRenderContext("minimal", TENANT, null);
        var blocks = avatar.contributeBlocks(ctx);
        assertFalse(blocks.isEmpty());
    }

    @SuppressWarnings("unchecked")
    private static <T> Instance<T> resolvedInstance(T value) {
        return new Instance<T>() {
            @Override public T get() { return value; }
            @Override public boolean isResolvable() { return true; }
            @Override public boolean isAmbiguous() { return false; }
            @Override public boolean isUnsatisfied() { return false; }
            @Override public Instance<T> select(Annotation... qualifiers) { return this; }
            @Override public <U extends T> Instance<U> select(Class<U> subtype, Annotation... qualifiers) { return (Instance<U>) this; }
            @Override public <U extends T> Instance<U> select(jakarta.enterprise.util.TypeLiteral<U> subtype, Annotation... qualifiers) { return (Instance<U>) this; }
            @Override public void destroy(T instance) {}
            @Override public Handle<T> getHandle() { return null; }
            @Override public Iterable<Handle<T>> handles() { return List.of(); }
            @Override public Iterator<T> iterator() { return List.of(value).iterator(); }
        };
    }

    @SuppressWarnings("unchecked")
    private static <T> Instance<T> unresolvedInstance() {
        return new Instance<T>() {
            @Override public T get() { throw new IllegalStateException("unresolved"); }
            @Override public boolean isResolvable() { return false; }
            @Override public boolean isAmbiguous() { return false; }
            @Override public boolean isUnsatisfied() { return true; }
            @Override public Instance<T> select(Annotation... qualifiers) { return this; }
            @Override public <U extends T> Instance<U> select(Class<U> subtype, Annotation... qualifiers) { return (Instance<U>) this; }
            @Override public <U extends T> Instance<U> select(jakarta.enterprise.util.TypeLiteral<U> subtype, Annotation... qualifiers) { return (Instance<U>) this; }
            @Override public void destroy(T instance) {}
            @Override public Handle<T> getHandle() { return null; }
            @Override public Iterable<Handle<T>> handles() { return List.of(); }
            @Override public Iterator<T> iterator() { return List.<T>of().iterator(); }
        };
    }
}

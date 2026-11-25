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

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import io.casehub.neocortex.cognition.prompt.CognitionRenderContext;
import io.casehub.neocortex.cognitive.index.CognitiveDefaults;
import io.casehub.neocortex.cognitive.index.CognitiveDefaultsRegistry;
import io.casehub.neocortex.cognitive.index.CognitiveProfile;
import io.casehub.neocortex.cognitive.index.CognitiveProfilesReloaded;
import io.casehub.neocortex.memory.MemoryDomain;
import io.casehub.neocortex.memory.MemoryInput;
import io.casehub.neocortex.memory.Subject;
import io.casehub.neocortex.memory.inmem.InMemoryMemoryStore;
import io.casehub.neocortex.mindmap.NodeInput;
import io.casehub.neocortex.mindmap.SubgraphTypes;
import io.casehub.neocortex.mindmap.inmem.InMemoryMindMapStore;
import io.casehub.platform.api.identity.CurrentPrincipal;
import jakarta.enterprise.inject.Instance;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.lang.annotation.Annotation;
import java.time.Instant;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CapabilityCompositionTest {

    private static final String TENANT = "test-tenant";

    private static final CurrentPrincipal PRINCIPAL = new CurrentPrincipal() {
        @Override public String actorId() { return "test"; }
        @Override public Set<String> groups() { return Set.of(); }
        @Override public String tenancyId() { return TENANT; }
        @Override public boolean isCrossTenantAdmin() { return false; }
    };

    private InMemoryMindMapStore mindMapStore;
    private InMemoryMemoryStore memoryStore;
    private CognitiveProfile cognitiveProfile;

    @BeforeEach
    void setUp() {
        mindMapStore = new InMemoryMindMapStore();
        memoryStore = new InMemoryMemoryStore(PRINCIPAL);
        cognitiveProfile = new CognitiveProfile(mindMapStore, memoryStore);
    }

    private static CognitiveDefaults loadProfile(String name) throws IOException {
        var mapper = new ObjectMapper(new YAMLFactory())
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        try (var is = CapabilityCompositionTest.class.getClassLoader()
                .getResourceAsStream("cognitive-profiles/" + name + ".yaml")) {
            assertNotNull(is, "profile " + name + " not found on classpath");
            return mapper.readValue(is, CognitiveDefaults.class);
        }
    }

    private CognitiveAvatar buildFullAvatar(CognitiveDefaults profile) {
        var capabilities = List.<CognitiveCapability>of(
            new BriefingCapability(profile.briefing()),
            new GoalCapability(mindMapStore),
            new TemporalCapability(mindMapStore),
            new MemoryCapability(memoryStore),
            new EntityKnowledgeCapability(cognitiveProfile),
            new DispositionCapability(profile),
            new NeedsCapability(mindMapStore, false),
            new ConsolidationCapability(mindMapStore, false),
            new AppraisalCapability(memoryStore, false),
            new InnerThoughtsCapability(memoryStore, false));
        var avatar = new CognitiveAvatar(profile, capabilities);
        avatar.initialize(profile.agentId(), TENANT);
        return avatar;
    }

    private void seedStores() {
        mindMapStore.addNode(NodeInput.of("Master Bayesian inference", SubgraphTypes.GOAL)
            .withProperties(Map.of("priority", "0.9", "horizon", "medium")), TENANT);
        mindMapStore.addNode(NodeInput.of("Publish research paper", SubgraphTypes.GOAL)
            .withProperties(Map.of("priority", "0.6")), TENANT);

        mindMapStore.addNode(NodeInput.of("Weekly review", SubgraphTypes.ACTIVITY)
            .withValidFrom(Instant.now().minusSeconds(3600)), TENANT);

        mindMapStore.addNode(NodeInput.of("Alice", SubgraphTypes.PERSON)
            .withProperties(Map.of("description", "Research collaborator")), TENANT);

        memoryStore.store(MemoryInput.of(
            Subject.of("person", "Alice"), new MemoryDomain("experience"),
            TENANT, "Discussed Bayesian networks with Alice"));
        memoryStore.store(MemoryInput.of(
            Subject.of("person", "Alice"), new MemoryDomain("experience"),
            TENANT, "Alice suggested using junction trees for inference"));
    }

    // --- Scenario 1: Prompt ordering with all capabilities ---

    @Test
    void allCapabilities_renderInBlockTagOrder() throws IOException {
        seedStores();
        var profile = loadProfile("stoic-mentor");
        var avatar = buildFullAvatar(profile);

        var ctx = new CognitionRenderContext("stoic-mentor", TENANT, "Alice");
        var blocks = avatar.contributeBlocks(ctx);

        var tags = blocks.stream().map(b -> b.tag()).toList();
        assertTrue(tags.contains("BEHAVIORAL"), "Missing BEHAVIORAL block");
        assertTrue(tags.contains("PERSONALITY"), "Missing PERSONALITY block");
        assertTrue(tags.contains("GOALS"), "Missing GOALS block");
        assertTrue(tags.contains("MEMORY"), "Missing MEMORY block");
        assertTrue(blocks.size() >= 5, "Expected at least 5 blocks with seeded data, got "
            + blocks.size() + " — tags: " + tags);

        int prevOrdinal = -1;
        for (var block : blocks) {
            var tag = block.tag();
            if (tag != null) {
                var matchingSection = avatar.promptSections().stream()
                    .filter(s -> s.blockTag() != null && s.blockTag().name().equals(tag))
                    .findFirst();
                if (matchingSection.isPresent()) {
                    int ordinal = matchingSection.get().blockTag().sortOrdinal();
                    assertTrue(ordinal >= prevOrdinal,
                        "Block " + tag + " (ordinal " + ordinal + ") appeared after ordinal " + prevOrdinal);
                    prevOrdinal = ordinal;
                }
            }
        }
    }

    @Test
    void allCapabilities_promptSectionsOrderedByBlockTag() throws IOException {
        seedStores();
        var profile = loadProfile("stoic-mentor");
        var avatar = buildFullAvatar(profile);

        var sections = avatar.promptSections();
        assertEquals(10, sections.size(), "All 10 capabilities should produce a prompt section");

        int prevOrdinal = -1;
        for (var section : sections) {
            if (section.blockTag() != null) {
                int ordinal = section.blockTag().sortOrdinal();
                assertTrue(ordinal >= prevOrdinal,
                    section.blockTag().name() + " (ordinal " + ordinal +
                    ") is out of order — previous was " + prevOrdinal);
                prevOrdinal = ordinal;
            }
        }
    }

    // --- Scenario 2: Resource contention ---

    @Test
    void concurrentContributeBlocks_producesConsistentResults() throws Exception {
        seedStores();
        var profile = loadProfile("stoic-mentor");
        var avatar = buildFullAvatar(profile);
        var ctx = new CognitionRenderContext("stoic-mentor", TENANT, "Alice");

        int threadCount = 8;
        var latch = new CountDownLatch(1);
        var results = new CopyOnWriteArrayList<Integer>();
        var errors = new CopyOnWriteArrayList<Throwable>();

        try (ExecutorService executor = Executors.newFixedThreadPool(threadCount)) {
            for (int i = 0; i < threadCount; i++) {
                executor.submit(() -> {
                    try {
                        latch.await();
                        var blocks = avatar.contributeBlocks(ctx);
                        results.add(blocks.size());
                    } catch (Throwable t) {
                        errors.add(t);
                    }
                });
            }
            latch.countDown();
            executor.shutdown();
            assertTrue(executor.awaitTermination(10, TimeUnit.SECONDS));
        }

        assertTrue(errors.isEmpty(), "Concurrent access produced errors: " + errors);
        assertEquals(threadCount, results.size(), "Not all threads completed");
        var firstResult = results.getFirst();
        assertTrue(results.stream().allMatch(r -> r.equals(firstResult)),
            "Concurrent access produced inconsistent block counts: " + results);
    }

    // --- Scenario 3: Empty state graceful degradation ---

    @Test
    void allCapabilitiesEnabled_emptyStores_noCrash() throws IOException {
        var profile = loadProfile("stoic-mentor");
        var avatar = buildFullAvatar(profile);

        var ctx = new CognitionRenderContext("stoic-mentor", TENANT, null);
        var blocks = avatar.contributeBlocks(ctx);

        assertNotNull(blocks);
        assertTrue(blocks.stream().anyMatch(b -> "PERSONALITY".equals(b.tag())),
            "Briefing should render even with empty stores");
        assertTrue(blocks.stream().anyMatch(b -> "BEHAVIORAL".equals(b.tag())),
            "Disposition should render even with empty stores");
    }

    @Test
    void allCapabilitiesEnabled_emptyStores_storeBackedCapabilitiesReturnNull() throws IOException {
        var profile = loadProfile("stoic-mentor");
        var avatar = buildFullAvatar(profile);

        var ctx = new CognitionRenderContext("stoic-mentor", TENANT, null);
        var blocks = avatar.contributeBlocks(ctx);

        assertEquals(2, blocks.size(),
            "Only briefing and disposition should render with empty stores — got tags: "
            + blocks.stream().map(b -> b.tag()).toList());
        assertFalse(blocks.stream().anyMatch(b -> "GOALS".equals(b.tag())),
            "Goals should not render with empty store");
        assertFalse(blocks.stream().anyMatch(b -> "MEMORY".equals(b.tag())),
            "Memory should not render with empty store");
    }

    // --- Scenario 4: Profile variations ---

    @Test
    void sameCapabilities_differentProfiles_produceDifferentDisposition() throws IOException {
        seedStores();
        var stoic = loadProfile("stoic-mentor");
        var bold = loadProfile("bold-strategist");

        var stoicAvatar = buildFullAvatar(stoic);
        var boldAvatar = buildFullAvatar(bold);

        var stoicCtx = new CognitionRenderContext("stoic-mentor", TENANT, null);
        var boldCtx = new CognitionRenderContext("bold-strategist", TENANT, null);

        var stoicBlocks = stoicAvatar.contributeBlocks(stoicCtx);
        var boldBlocks = boldAvatar.contributeBlocks(boldCtx);

        var stoicDisposition = stoicBlocks.stream()
            .filter(b -> "BEHAVIORAL".equals(b.tag()))
            .findFirst();
        var boldDisposition = boldBlocks.stream()
            .filter(b -> "BEHAVIORAL".equals(b.tag()))
            .findFirst();

        assertTrue(stoicDisposition.isPresent(), "Stoic disposition missing");
        assertTrue(boldDisposition.isPresent(), "Bold disposition missing");
        assertNotEquals(stoicDisposition.get().content(), boldDisposition.get().content(),
            "Different profiles should produce different disposition output");
    }

    @Test
    void sameCapabilities_differentProfiles_produceDifferentBriefing() throws IOException {
        seedStores();
        var stoic = loadProfile("stoic-mentor");
        var bold = loadProfile("bold-strategist");

        var stoicAvatar = buildFullAvatar(stoic);
        var boldAvatar = buildFullAvatar(bold);

        var stoicCtx = new CognitionRenderContext("stoic-mentor", TENANT, null);
        var boldCtx = new CognitionRenderContext("bold-strategist", TENANT, null);

        var stoicBriefing = stoicAvatar.contributeBlocks(stoicCtx).stream()
            .filter(b -> "PERSONALITY".equals(b.tag()))
            .findFirst().orElseThrow();
        var boldBriefing = boldAvatar.contributeBlocks(boldCtx).stream()
            .filter(b -> "PERSONALITY".equals(b.tag()))
            .findFirst().orElseThrow();

        assertTrue(stoicBriefing.content().contains("duty"));
        assertTrue(boldBriefing.content().contains("ambitious"));
        assertNotEquals(stoicBriefing.content(), boldBriefing.content());
    }

    @Test
    void sameCapabilities_differentProfiles_shareGoalData() throws IOException {
        seedStores();
        var stoic = loadProfile("stoic-mentor");
        var bold = loadProfile("bold-strategist");

        var stoicAvatar = buildFullAvatar(stoic);
        var boldAvatar = buildFullAvatar(bold);

        var stoicCtx = new CognitionRenderContext("stoic-mentor", TENANT, null);
        var boldCtx = new CognitionRenderContext("bold-strategist", TENANT, null);

        var stoicGoals = stoicAvatar.contributeBlocks(stoicCtx).stream()
            .filter(b -> "GOALS".equals(b.tag()))
            .findFirst();
        var boldGoals = boldAvatar.contributeBlocks(boldCtx).stream()
            .filter(b -> "GOALS".equals(b.tag()))
            .findFirst();

        assertTrue(stoicGoals.isPresent() && boldGoals.isPresent(),
            "Both avatars should see goals from the same store");
        assertEquals(stoicGoals.get().content(), boldGoals.get().content(),
            "Same store data should produce same goal output regardless of profile");
    }

    // --- Scenario 5: Hot-reload ---

    @Test
    void hotReload_clearsCacheAndRebuildsWithNewProfile() throws IOException {
        seedStores();
        var stoicProfile = loadProfile("stoic-mentor");
        var boldProfile = loadProfile("bold-strategist");

        var registry = CognitiveDefaultsRegistry.forTesting(stoicProfile);
        var producer = new CognitiveAvatarProducer(
            registry,
            resolvedInstance(mindMapStore),
            resolvedInstance(memoryStore),
            resolvedInstance(cognitiveProfile),
            unresolvedInstance());

        var before = producer.create("stoic-mentor", TENANT);
        var beforeCtx = new CognitionRenderContext("stoic-mentor", TENANT, null);
        var beforeBlocks = before.contributeBlocks(beforeCtx);
        assertTrue(beforeBlocks.stream().anyMatch(b ->
            "PERSONALITY".equals(b.tag()) && b.content().contains("duty")));

        var updatedRegistry = CognitiveDefaultsRegistry.forTesting(boldProfile);
        var updatedProducer = new CognitiveAvatarProducer(
            updatedRegistry,
            resolvedInstance(mindMapStore),
            resolvedInstance(memoryStore),
            resolvedInstance(cognitiveProfile),
            unresolvedInstance());

        updatedProducer.create("bold-strategist", TENANT);
        updatedProducer.onProfileReload(new CognitiveProfilesReloaded(List.of()));
        var after = updatedProducer.create("bold-strategist", TENANT);
        var afterCtx = new CognitionRenderContext("bold-strategist", TENANT, null);
        var afterBlocks = after.contributeBlocks(afterCtx);
        assertTrue(afterBlocks.stream().anyMatch(b ->
            "PERSONALITY".equals(b.tag()) && b.content().contains("ambitious")));
    }

    @Test
    void hotReload_newAvatarSeesExistingStoreData() throws IOException {
        seedStores();
        var profile = loadProfile("stoic-mentor");
        var registry = CognitiveDefaultsRegistry.forTesting(profile);
        var producer = new CognitiveAvatarProducer(
            registry,
            resolvedInstance(mindMapStore),
            resolvedInstance(memoryStore),
            resolvedInstance(cognitiveProfile),
            unresolvedInstance());

        producer.create("stoic-mentor", TENANT);
        producer.onProfileReload(new CognitiveProfilesReloaded(List.of()));

        var refreshed = producer.create("stoic-mentor", TENANT);
        var ctx = new CognitionRenderContext("stoic-mentor", TENANT, null);
        var blocks = refreshed.contributeBlocks(ctx);

        assertTrue(blocks.stream().anyMatch(b -> "GOALS".equals(b.tag())),
            "Refreshed avatar should see goal data from stores");
        assertTrue(blocks.stream().anyMatch(b -> "MEMORY".equals(b.tag())),
            "Refreshed avatar should see memory data from stores");
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
            @Override
            public T get()                                                                                                    {throw new IllegalStateException("unresolved");}

            @Override
            public boolean isResolvable()                                                                                     {return false;}

            @Override
            public boolean isAmbiguous()                                                                                      {return false;}

            @Override
            public boolean isUnsatisfied()                                                                                    {return true;}

            @Override
            public Instance<T> select(Annotation... qualifiers)                                                               {return this;}

            @Override
            public <U extends T> Instance<U> select(Class<U> subtype, Annotation... qualifiers)                               {return (Instance<U>) this;}

            @Override
            public <U extends T> Instance<U> select(jakarta.enterprise.util.TypeLiteral<U> subtype, Annotation... qualifiers) {return (Instance<U>) this;}

            @Override
            public void destroy(T instance)                                                                                   {}

            @Override
            public Handle<T> getHandle()                                                                                      {return null;}

            @Override
            public Iterable<Handle<T>> handles()                                                                              {return List.of();}

            @Override
            public Iterator<T> iterator()                                                                                     {return List.<T>of().iterator();}
        };
    }

}

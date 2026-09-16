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
package io.casehub.neocortex.cognitive.index;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

class CognitiveProfileWatcherTest {

    @TempDir Path tempDir;

    @Test
    void loadProfilesFromDirectory_readsYamlFiles() throws IOException {
        Files.writeString(tempDir.resolve("agent-x.yaml"), "agentId: agent-x\n");

        Map<String, CognitiveDefaults> profiles =
            CognitiveProfileWatcher.loadProfilesFromDirectory(tempDir);

        assertThat(profiles).hasSize(1);
        assertThat(profiles.get("agent-x").agentId()).isEqualTo("agent-x");
    }

    @Test
    void loadProfilesFromDirectory_ignoresHiddenFiles() throws IOException {
        Files.writeString(tempDir.resolve("agent-x.yaml"), "agentId: agent-x\n");
        Files.writeString(tempDir.resolve(".hidden.yaml"), "agentId: hidden\n");

        Map<String, CognitiveDefaults> profiles =
            CognitiveProfileWatcher.loadProfilesFromDirectory(tempDir);

        assertThat(profiles).hasSize(1);
        assertThat(profiles.containsKey("agent-x")).isTrue();
    }

    @Test
    void merge_filesystemOverridesClasspath() {
        var classpath = Map.of(
            "alice", CognitiveDefaults.empty("alice"),
            "bob", CognitiveDefaults.empty("bob"));
        var filesystem = Map.of(
            "alice", CognitiveDefaults.empty("alice").withTenantId("overridden"));

        var merged = CognitiveProfileWatcher.merge(classpath, filesystem);

        assertThat(merged).hasSize(2);
        assertThat(merged.get("alice").tenantId()).isEqualTo("overridden");
        assertThat(merged.get("bob").agentId()).isEqualTo("bob");
    }

    @Test
    void merge_filesystemAddsNew() {
        var classpath = Map.of("alice", CognitiveDefaults.empty("alice"));
        var filesystem = Map.of("carol", CognitiveDefaults.empty("carol"));

        var merged = CognitiveProfileWatcher.merge(classpath, filesystem);

        assertThat(merged).hasSize(2);
    }

    @Test
    void reloadProfiles_invalidYaml_keepsPreviousState() throws IOException {
        Files.writeString(tempDir.resolve("good.yaml"), "agentId: good\n");
        var registry = CognitiveDefaultsRegistry.forTesting(
            CognitiveDefaults.empty("original"));

        var watcher = new CognitiveProfileWatcher(
            registry, null, e -> {}, tempDir, null);
        watcher.snapshotBaselines();
        watcher.reloadProfiles();

        assertThat(registry.forAgent("good")).isPresent();

        Files.writeString(tempDir.resolve("bad.yaml"), "{{invalid yaml");

        watcher.reloadProfiles();

        assertThat(registry.forAgent("good")).isPresent();
    }

    @Test
    void fileChange_triggersReloadAndFiresEvent() throws Exception {
        Files.writeString(tempDir.resolve("agent-1.yaml"), "agentId: agent-1\n");

        var registry = CognitiveDefaultsRegistry.forTesting(
            new CognitiveDefaults[0]);
        var firedEvent = new AtomicReference<CognitiveProfilesReloaded>();
        var watcher = new CognitiveProfileWatcher(
            registry, null, firedEvent::set, tempDir, null);
        watcher.snapshotBaselines();
        watcher.reloadProfiles();
        watcher.startWatching();

        try {
            assertThat(registry.forAgent("agent-1")).isPresent();

            Thread.sleep(1000);

            Files.writeString(tempDir.resolve("agent-2.yaml"),
                "agentId: agent-2\n");

            await().atMost(15, TimeUnit.SECONDS).untilAsserted(() -> {
                assertThat(registry.forAgent("agent-2")).isPresent();
                assertThat(firedEvent.get()).isNotNull();
                assertThat(firedEvent.get().profiles()).hasSize(2);
            });
        } finally {
            watcher.stopWatching();
        }
    }

    @Test
    void rulesChange_doesNotFireProfilesEvent(@TempDir Path rulesDir) throws Exception {
        Files.writeString(rulesDir.resolve("test-rules.yaml"),
            "traitRules:\n  - trait: TestTrait\n    when:\n      hasEdgeTypes: [knows]\n");

        var registry = CognitiveDefaultsRegistry.forTesting(
            new CognitiveDefaults[0]);
        var ruleRegistry = DeclarativeRuleRegistry.of(
            List.of(), List.of());
        var firedEvent = new AtomicReference<CognitiveProfilesReloaded>();
        var watcher = new CognitiveProfileWatcher(
            registry, ruleRegistry, firedEvent::set, null, rulesDir);
        watcher.snapshotBaselines();
        watcher.reloadRules();
        watcher.startWatching();

        try {
            Thread.sleep(1000);

            Files.writeString(rulesDir.resolve("new-rule.yaml"),
                "traitRules:\n  - trait: NewTrait\n    when:\n      hasEdgeTypes: [likes]\n");

            await().atMost(15, TimeUnit.SECONDS).untilAsserted(() ->
                assertThat(ruleRegistry.traitRules(null)).anyMatch(
                    r -> r.traitName().equals("NewTrait")));

            assertThat(firedEvent.get()).isNull();
        } finally {
            watcher.stopWatching();
        }
    }

    @Test
    void watcherCreationFailure_degradesGracefully(@TempDir Path failDir) throws IOException {
        var registry = CognitiveDefaultsRegistry.forTesting(
                CognitiveDefaults.empty("existing"));
        // Use a file (not a directory) as the watch path — DirectoryWatcher.builder().path() will fail
        Path notADir = failDir.resolve("not-a-directory.txt");
        Files.writeString(notADir, "I am a file");
        var watcher = new CognitiveProfileWatcher(
                registry, null, e -> {}, notADir, null);

        watcher.snapshotBaselines();
        watcher.startWatching();

        assertThat(registry.forAgent("existing")).isPresent();
        watcher.stopWatching();
    }

    @Test
    void emptyDirectory_usesClasspathBaseline() throws IOException {
        var registry = CognitiveDefaultsRegistry.forTesting(
            CognitiveDefaults.empty("classpath-agent"));
        var watcher = new CognitiveProfileWatcher(
            registry, null, e -> {}, tempDir, null);
        watcher.snapshotBaselines();
        watcher.reloadProfiles();

        assertThat(registry.forAgent("classpath-agent")).isPresent();
    }
}

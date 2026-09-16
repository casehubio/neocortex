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

import com.fasterxml.jackson.databind.ObjectMapper;
import io.casehub.neocortex.mindmap.DeclarativeDerivedEdgeRule;
import io.casehub.neocortex.mindmap.DeclarativeTraitRule;
import io.methvin.watcher.DirectoryChangeEvent;
import io.methvin.watcher.DirectoryWatcher;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Event;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.logging.Level;
import java.util.logging.Logger;

@ApplicationScoped
public class CognitiveProfileWatcher {

    private static final Logger LOG = Logger.getLogger(CognitiveProfileWatcher.class.getName());
    private static final long DEBOUNCE_MS = 500;

    private final CognitiveDefaultsRegistry registry;
    private final DeclarativeRuleRegistry ruleRegistry;
    private final Consumer<CognitiveProfilesReloaded> reloadSink;
    private final Optional<Path> profilesDir;
    private final Optional<Path> rulesDir;

    private Map<String, CognitiveDefaults> classpathProfiles;
    private RuleFile classpathRules;

    private volatile DirectoryWatcher profilesWatcher;
    private volatile DirectoryWatcher rulesWatcher;
    private ScheduledExecutorService debounceExecutor;
    private ScheduledFuture<?> pendingProfilesFlush;
    private ScheduledFuture<?> pendingRulesFlush;
    private final Object flushLock = new Object();

    @Inject
    CognitiveProfileWatcher(CognitiveDefaultsRegistry registry,
                            DeclarativeRuleRegistry ruleRegistry,
                            Event<CognitiveProfilesReloaded> reloadEvent,
                            @ConfigProperty(name = "casehub.cognitive.profiles-dir")
                            Optional<Path> profilesDir,
                            @ConfigProperty(name = "casehub.cognitive.rules-dir")
                            Optional<Path> rulesDir) {
        this.registry     = registry;
        this.ruleRegistry = ruleRegistry;
        this.reloadSink   = reloadEvent::fire;
        this.profilesDir  = profilesDir;
        this.rulesDir     = rulesDir;
    }

    CognitiveProfileWatcher(CognitiveDefaultsRegistry registry,
                            DeclarativeRuleRegistry ruleRegistry,
                            Consumer<CognitiveProfilesReloaded> reloadSink,
                            Path profilesDir, Path rulesDir) {
        this.registry     = registry;
        this.ruleRegistry = ruleRegistry;
        this.reloadSink   = reloadSink;
        this.profilesDir  = Optional.ofNullable(profilesDir);
        this.rulesDir     = Optional.ofNullable(rulesDir);
    }

    @PostConstruct
    void init() {
        if (profilesDir.isEmpty() && rulesDir.isEmpty()) return;

        snapshotBaselines();

        profilesDir.ifPresent(dir -> {
            try {
                Files.createDirectories(dir);
                reloadProfiles();
            } catch (IOException e) {
                LOG.log(Level.WARNING, "Failed to create profiles directory: " + dir, e);
            }
        });

        rulesDir.ifPresent(dir -> {
            try {
                Files.createDirectories(dir);
                reloadRules();
            } catch (IOException e) {
                LOG.log(Level.WARNING, "Failed to create rules directory: " + dir, e);
            }
        });

        try {
            startWatching();
        } catch (Exception e) {
            LOG.log(Level.WARNING,
                "Failed to start file watchers — running with classpath-only profiles", e);
        }
    }

    @PreDestroy
    void destroy() {
        stopWatching();
    }

    void snapshotBaselines() {
        var profileMap = new LinkedHashMap<String, CognitiveDefaults>();
        registry.allProfiles().forEach(p -> profileMap.put(p.agentId(), p));
        this.classpathProfiles = Map.copyOf(profileMap);
        this.classpathRules = ruleRegistry != null
            ? ruleRegistry.currentGlobalRules() : new RuleFile(List.of(), List.of());
    }

    void reloadProfiles() {
        if (profilesDir.isEmpty()) return;
        try {
            var filesystem = loadProfilesFromDirectory(profilesDir.get());
            var merged = merge(classpathProfiles, filesystem);
            registry.reload(merged);
            LOG.info("Reloaded " + merged.size() + " cognitive profile(s)");
            reloadSink.accept(new CognitiveProfilesReloaded(merged.values()));
        } catch (Exception e) {
            LOG.log(Level.WARNING,
                "Failed to reload cognitive profiles — keeping previous state", e);
        }
    }

    void reloadRules() {
        if (rulesDir.isEmpty() || ruleRegistry == null) return;
        try {
            var filesystem = loadRulesFromDirectory(rulesDir.get());

            var mergedTrait = new ArrayList<>(classpathRules.traitRules());
            var traitNamesSeen = new HashSet<String>();
            filesystem.traitRules().forEach(r -> traitNamesSeen.add(r.traitName()));
            mergedTrait.removeIf(r -> traitNamesSeen.contains(r.traitName()));
            mergedTrait.addAll(filesystem.traitRules());

            var mergedDerived = new ArrayList<>(classpathRules.derivedEdgeRules());
            var derivedNamesSeen = new HashSet<String>();
            filesystem.derivedEdgeRules().forEach(r -> derivedNamesSeen.add(r.name()));
            mergedDerived.removeIf(r -> derivedNamesSeen.contains(r.name()));
            mergedDerived.addAll(filesystem.derivedEdgeRules());

            ruleRegistry.reloadGlobalRules(mergedTrait, mergedDerived);
            LOG.info("Reloaded " + mergedTrait.size() + " global trait rule(s) and "
                + mergedDerived.size() + " global derived edge rule(s)");
        } catch (Exception e) {
            LOG.log(Level.WARNING,
                "Failed to reload global rules — keeping previous state", e);
        }
    }

    void startWatching() {
        debounceExecutor = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "cognitive-profile-watcher-debounce");
            t.setDaemon(true);
            return t;
        });

        profilesDir.ifPresent(dir -> {
            try {
                profilesWatcher = DirectoryWatcher.builder()
                    .path(dir)
                    .listener(this::onProfilesEvent)
                    .build();
                profilesWatcher.watchAsync();
                LOG.info("Watching cognitive profiles: " + dir);
            } catch (IOException e) {
                LOG.log(Level.WARNING,
                    "Failed to create profiles watcher — profiles will not auto-reload", e);
            }
        });

        rulesDir.ifPresent(dir -> {
            try {
                rulesWatcher = DirectoryWatcher.builder()
                    .path(dir)
                    .listener(this::onRulesEvent)
                    .build();
                rulesWatcher.watchAsync();
                LOG.info("Watching global rules: " + dir);
            } catch (IOException e) {
                LOG.log(Level.WARNING,
                    "Failed to create rules watcher — rules will not auto-reload", e);
            }
        });
    }

    void stopWatching() {
        synchronized (flushLock) {
            if (pendingProfilesFlush != null) {
                pendingProfilesFlush.cancel(false);
                pendingProfilesFlush = null;
            }
            if (pendingRulesFlush != null) {
                pendingRulesFlush.cancel(false);
                pendingRulesFlush = null;
            }
        }
        if (profilesWatcher != null) {
            try { profilesWatcher.close(); } catch (IOException e) {
                LOG.log(Level.WARNING, "Error closing profiles watcher", e);
            }
            profilesWatcher = null;
        }
        if (rulesWatcher != null) {
            try { rulesWatcher.close(); } catch (IOException e) {
                LOG.log(Level.WARNING, "Error closing rules watcher", e);
            }
            rulesWatcher = null;
        }
        if (debounceExecutor != null) {
            debounceExecutor.shutdownNow();
            debounceExecutor = null;
        }
    }

    static Map<String, CognitiveDefaults> loadProfilesFromDirectory(Path dir) throws IOException {
        ObjectMapper mapper = CognitiveDefaultsRegistry.createMapper();
        Map<String, CognitiveDefaults> loaded = new LinkedHashMap<>();
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(dir,
                p -> {
                    String name = p.getFileName().toString();
                    return (name.endsWith(".yaml") || name.endsWith(".yml"))
                        && !name.startsWith(".");
                })) {
            for (Path file : stream) {
                CognitiveDefaults defaults = mapper.readValue(
                    file.toFile(), CognitiveDefaults.class);
                CognitiveDefaultsRegistry.addProfile(loaded, defaults,
                    file.getFileName().toString());
            }
        }
        return loaded;
    }

    static RuleFile loadRulesFromDirectory(Path dir) throws IOException {
        ObjectMapper mapper = CognitiveDefaultsRegistry.createMapper();
        List<DeclarativeTraitRule> traitRules = new ArrayList<>();
        List<DeclarativeDerivedEdgeRule> derivedRules = new ArrayList<>();
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(dir,
                p -> {
                    String name = p.getFileName().toString();
                    return (name.endsWith(".yaml") || name.endsWith(".yml"))
                        && !name.startsWith(".");
                })) {
            for (Path file : stream) {
                RuleFile ruleFile = mapper.readValue(file.toFile(), RuleFile.class);
                traitRules.addAll(ruleFile.traitRules());
                derivedRules.addAll(ruleFile.derivedEdgeRules());
            }
        }
        return new RuleFile(traitRules, derivedRules);
    }

    static Map<String, CognitiveDefaults> merge(
            Map<String, CognitiveDefaults> classpath,
            Map<String, CognitiveDefaults> filesystem) {
        var merged = new LinkedHashMap<>(classpath);
        merged.putAll(filesystem);
        return merged;
    }

    private void onProfilesEvent(DirectoryChangeEvent event) {
        if (event.eventType() == DirectoryChangeEvent.EventType.OVERFLOW) {
            scheduleProfilesFlush();
            return;
        }
        if (event.path() == null || Files.isDirectory(event.path())) return;
        String name = event.path().getFileName().toString();
        if (!name.endsWith(".yaml") && !name.endsWith(".yml")) return;
        if (name.startsWith(".")) return;
        scheduleProfilesFlush();
    }

    private void onRulesEvent(DirectoryChangeEvent event) {
        if (event.eventType() == DirectoryChangeEvent.EventType.OVERFLOW) {
            scheduleRulesFlush();
            return;
        }
        if (event.path() == null || Files.isDirectory(event.path())) return;
        String name = event.path().getFileName().toString();
        if (!name.endsWith(".yaml") && !name.endsWith(".yml")) return;
        if (name.startsWith(".")) return;
        scheduleRulesFlush();
    }

    private void scheduleProfilesFlush() {
        synchronized (flushLock) {
            if (debounceExecutor == null || debounceExecutor.isShutdown()) return;
            if (pendingProfilesFlush != null) pendingProfilesFlush.cancel(false);
            pendingProfilesFlush = debounceExecutor.schedule(
                this::reloadProfiles, DEBOUNCE_MS, TimeUnit.MILLISECONDS);
        }
    }

    private void scheduleRulesFlush() {
        synchronized (flushLock) {
            if (debounceExecutor == null || debounceExecutor.isShutdown()) return;
            if (pendingRulesFlush != null) pendingRulesFlush.cancel(false);
            pendingRulesFlush = debounceExecutor.schedule(
                this::reloadRules, DEBOUNCE_MS, TimeUnit.MILLISECONDS);
        }
    }
}

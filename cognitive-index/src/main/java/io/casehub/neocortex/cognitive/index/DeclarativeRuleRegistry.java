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
import io.casehub.neocortex.mindmap.DerivedEdgeRule;
import io.casehub.neocortex.mindmap.TraitRule;
import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.logging.Logger;

@ApplicationScoped
public class DeclarativeRuleRegistry {

    private static final Logger LOG = Logger.getLogger(DeclarativeRuleRegistry.class.getName());

    private List<DeclarativeTraitRule>       globalTraitRules   = List.of();
    private List<DeclarativeDerivedEdgeRule> globalDerivedRules = List.of();

    @Inject
    CognitiveDefaultsRegistry cognitiveDefaults;

    DeclarativeRuleRegistry() {}

    DeclarativeRuleRegistry(List<DeclarativeTraitRule> globalTraitRules,
                            List<DeclarativeDerivedEdgeRule> globalDerivedRules,
                            CognitiveDefaultsRegistry cognitiveDefaults) {
        this.globalTraitRules   = List.copyOf(globalTraitRules);
        this.globalDerivedRules = List.copyOf(globalDerivedRules);
        this.cognitiveDefaults  = cognitiveDefaults;
    }

    @PostConstruct
    void init() {
        try {
            var result = loadGlobalRules("rules/",
                                         Thread.currentThread().getContextClassLoader());
            this.globalTraitRules   = result.traitRules();
            this.globalDerivedRules = result.derivedEdgeRules();
        } catch (IOException e) {
            LOG.warning("Failed to load global rules: " + e.getMessage());
        }
        if (!globalTraitRules.isEmpty() || !globalDerivedRules.isEmpty()) {
            LOG.info("Loaded " + globalTraitRules.size() + " global trait rule(s) and "
                     + globalDerivedRules.size() + " global derived edge rule(s)");
        }
    }

    public static DeclarativeRuleRegistry of(List<DeclarativeTraitRule> traitRules,
                                             List<DeclarativeDerivedEdgeRule> derivedEdgeRules) {
        return new DeclarativeRuleRegistry(traitRules, derivedEdgeRules, null);
    }


    public List<TraitRule> traitRules(String agentId) {
        var merged = new LinkedHashMap<String, TraitRule>();
        globalTraitRules.forEach(r -> merged.put(r.traitName(), r));
        if (agentId != null && cognitiveDefaults != null) {
            cognitiveDefaults.forAgent(agentId).ifPresent(defaults -> {
                if (defaults.traitRules() != null) {
                    defaults.traitRules().forEach(r -> merged.put(r.traitName(), r));
                }
            });
        }
        return List.copyOf(merged.values());
    }

    public List<TraitRule> allTraitRules() {
        var merged = new LinkedHashMap<String, TraitRule>();
        globalTraitRules.forEach(r -> merged.put(r.traitName(), r));
        if (cognitiveDefaults != null) {
            for (CognitiveDefaults defaults : cognitiveDefaults.allProfiles()) {
                if (defaults.traitRules() != null) {
                    defaults.traitRules().forEach(r -> merged.put(r.traitName(), r));
                }
            }
        }
        return List.copyOf(merged.values());
    }

    public List<DerivedEdgeRule> derivedEdgeRules(String agentId) {
        var merged = new LinkedHashMap<String, DerivedEdgeRule>();
        globalDerivedRules.forEach(r -> merged.put(r.name(), r));
        if (agentId != null && cognitiveDefaults != null) {
            cognitiveDefaults.forAgent(agentId).ifPresent(defaults -> {
                if (defaults.derivedEdgeRules() != null) {
                    defaults.derivedEdgeRules().forEach(r -> merged.put(r.name(), r));
                }
            });
        }
        return List.copyOf(merged.values());
    }

    public List<DerivedEdgeRule> allDerivedEdgeRules() {
        var merged = new LinkedHashMap<String, DerivedEdgeRule>();
        globalDerivedRules.forEach(r -> merged.put(r.name(), r));
        if (cognitiveDefaults != null) {
            for (CognitiveDefaults defaults : cognitiveDefaults.allProfiles()) {
                if (defaults.derivedEdgeRules() != null) {
                    defaults.derivedEdgeRules().forEach(r -> merged.put(r.name(), r));
                }
            }
        }
        return List.copyOf(merged.values());
    }

    private static RuleFile loadGlobalRules(String rulesPath, ClassLoader classLoader) throws IOException {
        ObjectMapper                     mapper         = CognitiveDefaultsRegistry.createMapper();
        List<DeclarativeTraitRule>       traitRules     = new ArrayList<>();
        List<DeclarativeDerivedEdgeRule> derivedRules   = new ArrayList<>();
        String                           normalizedPath = rulesPath.endsWith("/") ? rulesPath : rulesPath + "/";

        Enumeration<URL> resources = classLoader.getResources(normalizedPath);
        while (resources.hasMoreElements()) {
            URL dirUrl = resources.nextElement();
            if ("file".equals(dirUrl.getProtocol())) {
                File dir = new File(dirUrl.getFile());
                if (dir.isDirectory()) {
                    File[] files = dir.listFiles((d, name) ->
                                                         name.endsWith(".yaml") || name.endsWith(".yml"));
                    if (files != null) {
                        for (File file : files) {
                            try (InputStream is = new FileInputStream(file)) {
                                RuleFile ruleFile = mapper.readValue(is, RuleFile.class);
                                traitRules.addAll(ruleFile.traitRules());
                                derivedRules.addAll(ruleFile.derivedEdgeRules());
                            }
                        }
                    }
                }
            }
        }
        return new RuleFile(traitRules, derivedRules);
    }

    static DeclarativeRuleRegistry loadFromClasspath(String rulesPath,
                                                     CognitiveDefaultsRegistry cognitiveDefaults,
                                                     ClassLoader classLoader) throws IOException {
        RuleFile loaded = loadGlobalRules(rulesPath, classLoader);
        return new DeclarativeRuleRegistry(loaded.traitRules(), loaded.derivedEdgeRules(), cognitiveDefaults);
    }
}

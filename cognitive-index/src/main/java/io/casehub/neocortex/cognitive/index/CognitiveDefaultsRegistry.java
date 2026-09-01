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
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import io.casehub.neocortex.memory.personality.PersonalityWeights;
import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;
import io.casehub.neocortex.mindmap.DeclarativeDerivedEdgeRule;
import io.casehub.neocortex.mindmap.DeclarativeTraitRule;
import io.casehub.neocortex.mindmap.RuleCondition;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.util.Collection;
import java.util.Enumeration;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.logging.Logger;

@ApplicationScoped
public class CognitiveDefaultsRegistry {

    private static final Logger LOG = Logger.getLogger(CognitiveDefaultsRegistry.class.getName());

    private Map<String, CognitiveDefaults> profiles;

    CognitiveDefaultsRegistry() {
        this.profiles = Map.of();
    }

    @PostConstruct
    void init() {
        try {
            var loaded = loadFromClasspath("cognitive-profiles/",
                Thread.currentThread().getContextClassLoader());
            this.profiles = loaded.profiles;
        } catch (IOException e) {
            LOG.warning("Failed to load cognitive profiles: " + e.getMessage());
        }
    }

    public Optional<CognitiveDefaults> forAgent(String agentId) {
        return Optional.ofNullable(profiles.get(agentId));
    }

    public CognitiveDefaults forAgentOrDefaults(String agentId) {
        return profiles.getOrDefault(agentId,
                                     new CognitiveDefaults(agentId, null, null, null, null, null, null, null, null, null));
    }

    public Collection<CognitiveDefaults> allProfiles() {
        return profiles.values();
    }

    static CognitiveDefaultsRegistry loadFromClasspath(String path, ClassLoader classLoader)
            throws IOException {
        ObjectMapper mapper = createMapper();
        Map<String, CognitiveDefaults> loaded = new LinkedHashMap<>();
        String normalizedPath = path.endsWith("/") ? path : path + "/";

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
                                CognitiveDefaults defaults = mapper.readValue(is, CognitiveDefaults.class);
                                addProfile(loaded, defaults, file.getName());
                            }
                        }
                    }
                }
            }
        }

        var registry = new CognitiveDefaultsRegistry();
        registry.profiles = Map.copyOf(loaded);
        if (!loaded.isEmpty()) {
            LOG.info("Loaded " + loaded.size() + " cognitive profile(s): "
                + String.join(", ", loaded.keySet()));
        }
        return registry;
    }

    static ObjectMapper createMapper() {
        ObjectMapper mapper = new ObjectMapper(new YAMLFactory()).findAndRegisterModules();
        SimpleModule module = new SimpleModule();
        module.addDeserializer(PersonalityWeights.class, new PersonalityWeightsDeserializer());
        module.addDeserializer(RuleCondition.class, new RuleConditionDeserializer());
        module.addDeserializer(DeclarativeTraitRule.class, new DeclarativeTraitRuleDeserializer());
        module.addDeserializer(DeclarativeDerivedEdgeRule.class, new DeclarativeDerivedEdgeRuleDeserializer());
        mapper.registerModule(module);
        return mapper;
    }

    private static void addProfile(Map<String, CognitiveDefaults> map,
            CognitiveDefaults defaults, String source) {
        if (map.containsKey(defaults.agentId())) {
            throw new IllegalStateException(
                "Duplicate agentId '" + defaults.agentId()
                    + "' in cognitive profile: " + source);
        }
        map.put(defaults.agentId(), defaults);
    }
}

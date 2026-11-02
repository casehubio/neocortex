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
package io.casehub.neocortex.cognitive.eidos;

import io.casehub.eidos.api.AgentDescriptor;
import io.casehub.eidos.api.AgentDisposition;
import io.casehub.eidos.api.AgentGoal;
import io.casehub.eidos.api.AgentMatch;
import io.casehub.eidos.api.AgentQuery;
import io.casehub.eidos.api.AgentRegistry;
import io.casehub.eidos.api.DispositionValue;
import io.casehub.eidos.api.GoalPriority;
import io.casehub.eidos.api.Visibility;
import io.casehub.neocortex.cognitive.index.CognitiveDefaults;
import io.casehub.neocortex.cognitive.index.CognitiveDefaultsRegistry;
import io.casehub.neocortex.cognitive.index.DescriptorView;
import io.casehub.neocortex.cognitive.index.DispositionAxes;
import io.casehub.neocortex.cognitive.index.WeightedTerm;
import io.casehub.neocortex.memory.mood.MoodBaseline;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class EidosCognitiveProfileBridgeTest {

    @Test
    void registersProfileWithAllDerivationPathways() {
        var registry = CognitiveDefaultsRegistry.forTesting();
        var bridge = new EidosCognitiveProfileBridge(registry);

        bridge.registerFromDescriptor(DescriptorView.of(
                "muttley",
                new DispositionAxes("competitive", "flexible", "bold", "high", "competitive"),
                List.of(new WeightedTerm("Se", 0.8), new WeightedTerm("Te", 0.6)),
                List.of("cause chaos", "avoid punishment")
        ));

        var profile = registry.forAgent("muttley").orElseThrow();
        assertEquals("muttley", profile.agentId());
        assertNotNull(profile.personalityProfile());
        assertNotNull(profile.moodBaseline());
        assertNotNull(profile.curiosity());
        assertNotNull(profile.cbrStrategy());
        assertNotNull(profile.socialCognition());
        assertNotNull(profile.graphStructure());
        assertNotNull(profile.extractionBias());
        assertNotNull(profile.appraisalWeights());
        assertNotNull(profile.habituationConfig());
    }

    @Test
    void differentDispositionsProduceDifferentProfiles() {
        var registry = CognitiveDefaultsRegistry.forTesting();
        var bridge = new EidosCognitiveProfileBridge(registry);

        bridge.registerFromDescriptor(DescriptorView.of(
                "muttley",
                new DispositionAxes("competitive", "flexible", "bold", "high", "competitive"),
                List.of(new WeightedTerm("Se", 0.8), new WeightedTerm("Te", 0.6)),
                List.of("cause chaos")
        ));

        bridge.registerFromDescriptor(DescriptorView.of(
                "penelope",
                new DispositionAxes("cooperative", "strict", "conservative", "moderate", "cooperative"),
                List.of(new WeightedTerm("Fe", 0.9), new WeightedTerm("Fi", 0.7)),
                List.of("help others", "stay safe")
        ));

        var m = registry.forAgent("muttley").orElseThrow();
        var p = registry.forAgent("penelope").orElseThrow();

        assertNotEquals(m.personalityProfile().personality(), p.personalityProfile().personality());
        assertNotEquals(m.moodBaseline(), p.moodBaseline());
        assertNotEquals(m.cbrStrategy(), p.cbrStrategy());
        assertNotEquals(m.socialCognition(), p.socialCognition());
    }

    @Test
    void existingYamlProfileMergesWithDerived() {
        var yamlMood = new MoodBaseline(0.9, -0.5, 0.3);
        var existing = CognitiveDefaults.empty("muttley").withMoodBaseline(yamlMood);
        var registry = CognitiveDefaultsRegistry.forTesting(existing);

        var bridge = new EidosCognitiveProfileBridge(registry);
        bridge.registerFromDescriptor(DescriptorView.of(
                "muttley",
                new DispositionAxes("competitive", "flexible", "bold", "high", "competitive"),
                List.of(new WeightedTerm("Se", 0.8)),
                List.of("cause chaos")
        ));

        var profile = registry.forAgent("muttley").orElseThrow();
        assertEquals(yamlMood, profile.moodBaseline(), "YAML mood baseline should be preserved");
        assertNotNull(profile.cbrStrategy(), "derived CBR strategy should fill the gap");
        assertNotNull(profile.socialCognition(), "derived social cognition should fill the gap");
    }

    @Test
    void toDescriptorViewExtractsAxesAndProfile() {
        var descriptor = AgentDescriptor.builder()
                .agentId("dick-dastardly")
                .name("Dick Dastardly")
                .slot("villain")
                .tenancyId("manor")
                .disposition(AgentDisposition.builder()
                        .socialOrient("competitive")
                        .ruleFollowing("flexible")
                        .riskAppetite("bold")
                        .autonomy("high")
                        .conflictMode("competing")
                        .dispositionProfile(
                                new DispositionValue("Te", 0.8),
                                new DispositionValue("Se", 0.6))
                        .build())
                .goals(List.of(
                        new AgentGoal("win race", "win at all costs",
                                GoalPriority.PRIMARY, Visibility.PUBLIC, List.of(), null),
                        new AgentGoal("cheat", "use underhanded tactics",
                                GoalPriority.SECONDARY, Visibility.PRIVATE, List.of(), null)))
                .build();

        var view = EidosCognitiveProfileBridge.toDescriptorView(descriptor);

        assertEquals("dick-dastardly", view.agentId());
        assertEquals("competitive", view.disposition().socialOrient());
        assertEquals("flexible", view.disposition().ruleFollowing());
        assertEquals("bold", view.disposition().riskAppetite());
        assertEquals("high", view.disposition().autonomy());
        assertEquals("competing", view.disposition().conflictMode());
        assertEquals(2, view.dispositionProfile().size());
        assertEquals("Te", view.dispositionProfile().get(0).term());
        assertEquals(0.8, view.dispositionProfile().get(0).weight(), 0.001);
        assertEquals(List.of("win race", "cheat"), view.goals());
    }

    @Test
    void bridgeAllRegistersFromAgentRegistry() {
        var descriptor = AgentDescriptor.builder()
                .agentId("penelope")
                .name("Penelope Pitstop")
                .slot("hero")
                .tenancyId("manor")
                .disposition(AgentDisposition.builder()
                        .socialOrient("cooperative")
                        .ruleFollowing("strict")
                        .riskAppetite("conservative")
                        .autonomy("moderate")
                        .conflictMode("cooperative")
                        .dispositionProfile(new DispositionValue("Fe", 0.9))
                        .build())
                .goals(List.of(new AgentGoal("help others", "assist fellow racers",
                        GoalPriority.PRIMARY, Visibility.PUBLIC, List.of(), null)))
                .build();

        var stubRegistry = new AgentRegistry() {
            @Override
            public void register(AgentDescriptor d) {}
            @Override
            public Optional<AgentDescriptor> findById(String agentId, String tenancyId) {
                return Optional.empty();
            }
            @Override
            public List<AgentMatch> find(AgentQuery query) {
                assertEquals("manor", query.tenancyId());
                return List.of(new AgentMatch(descriptor, null));
            }
        };

        var registry = CognitiveDefaultsRegistry.forTesting();
        var bridge = new EidosCognitiveProfileBridge(registry, stubRegistry);
        bridge.bridgeAll("manor");

        var profile = registry.forAgent("penelope");
        assertTrue(profile.isPresent());
        assertNotNull(profile.get().moodBaseline());
    }

    @Test
    void emptyDispositionProfileProducesNullGraphStructure() {
        var registry = CognitiveDefaultsRegistry.forTesting();
        var bridge = new EidosCognitiveProfileBridge(registry);

        bridge.registerFromDescriptor(DescriptorView.of(
                "no-profile",
                new DispositionAxes("cooperative", "strict", "conservative", "moderate", "cooperative"),
                List.of(),
                List.of("survive")
        ));

        var profile = registry.forAgent("no-profile").orElseThrow();
        assertNotNull(profile.moodBaseline());
        assertNull(profile.graphStructure(), "empty disposition profile should produce null graph structure");
    }
}

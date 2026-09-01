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

import java.io.IOException;

import static org.assertj.core.api.Assertions.assertThat;

class CognitiveDefaultsRegistryTest {

    private CognitiveDefaultsRegistry registry(String path) throws IOException {
        return CognitiveDefaultsRegistry.loadFromClasspath(path,
            Thread.currentThread().getContextClassLoader());
    }

    @Test
    void loadFromClasspath_findsAllProfiles() throws IOException {
        var reg = registry("cognitive-profiles/");

        assertThat(reg.allProfiles()).hasSize(2);
    }

    @Test
    void forAgent_returnsMatchingProfile() throws IOException {
        var reg = registry("cognitive-profiles/");

        var alice = reg.forAgent("alice");
        assertThat(alice).isPresent();
        assertThat(alice.get().agentId()).isEqualTo("alice");
        assertThat(alice.get().personality()).isNotNull();
    }

    @Test
    void forAgent_returnsEmptyForUnknown() throws IOException {
        var reg = registry("cognitive-profiles/");

        assertThat(reg.forAgent("unknown")).isEmpty();
    }

    @Test
    void forAgentOrDefaults_returnsDefaultsForUnknown() throws IOException {
        var reg = registry("cognitive-profiles/");

        var defaults = reg.forAgentOrDefaults("unknown");
        assertThat(defaults.agentId()).isEqualTo("unknown");
        assertThat(defaults.personality()).isNull();
        assertThat(defaults.moodBaseline()).isNull();
        assertThat(defaults.curiosity()).isNull();
        assertThat(defaults.services()).isEmpty();
    }

    @Test
    void minimalProfile_loadsWithNullSections() throws IOException {
        var reg = registry("cognitive-profiles/");

        var bob = reg.forAgent("bob");
        assertThat(bob).isPresent();
        assertThat(bob.get().tenantId()).isNull();
        assertThat(bob.get().personality()).isNull();
        assertThat(bob.get().vocabulary()).isNull();
    }

    @Test
    void emptyPath_producesEmptyRegistry() throws IOException {
        var reg = registry("cognitive-profiles-nonexistent/");

        assertThat(reg.allProfiles()).isEmpty();
        assertThat(reg.forAgent("alice")).isEmpty();
    }

    @Test
    void forAgent_parsesTraitRules() throws IOException {
        var reg = registry("cognitive-profiles/");

        var alice = reg.forAgent("alice").orElseThrow();
        assertThat(alice.traitRules()).isNotNull().hasSize(1);
        assertThat(alice.traitRules().get(0).traitName()).isEqualTo("FamilyMember");
    }

    @Test
    void forAgent_parsesDerivedEdgeRules() throws IOException {
        var reg = registry("cognitive-profiles/");

        var alice = reg.forAgent("alice").orElseThrow();
        assertThat(alice.derivedEdgeRules()).isNotNull().hasSize(1);
        assertThat(alice.derivedEdgeRules().get(0).name()).isEqualTo("inverse-knows");
        assertThat(alice.derivedEdgeRules().get(0).derivations().get(0).properties())
                .containsEntry("perspective", "alice");
    }

    @Test
    void minimalProfile_hasNullRules() throws IOException {
        var reg = registry("cognitive-profiles/");

        var bob = reg.forAgent("bob").orElseThrow();
        assertThat(bob.traitRules()).isNull();
        assertThat(bob.derivedEdgeRules()).isNull();
    }

    @Test
    void forAgentOrDefaults_hasNullRules() throws IOException {
        var reg = registry("cognitive-profiles/");

        var defaults = reg.forAgentOrDefaults("unknown");
        assertThat(defaults.traitRules()).isNull();
        assertThat(defaults.derivedEdgeRules()).isNull();
    }
}

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
import io.casehub.neocortex.memory.MemoryDomain;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CognitiveDefaultsParserTest {

    private ObjectMapper mapper;

    @BeforeEach
    void setUp() {
        mapper = CognitiveDefaultsRegistry.createMapper();
    }

    private CognitiveDefaults parse(String resource) throws IOException {
        try (InputStream is = getClass().getClassLoader().getResourceAsStream(resource)) {
            return mapper.readValue(is, CognitiveDefaults.class);
        }
    }

    @Test
    void fullProfile_parsesAllSections() throws IOException {
        var defaults = parse("cognitive-profiles/alice.yaml");

        assertThat(defaults.agentId()).isEqualTo("alice");
        assertThat(defaults.tenantId()).isEqualTo("family-1");
        assertThat(defaults.personality()).isNotNull();
        assertThat(defaults.personality().getWeight(new MemoryDomain("experience"))).isEqualTo(1.5);
        assertThat(defaults.personality().getWeight(new MemoryDomain("relationship"))).isEqualTo(0.8);
        assertThat(defaults.moodBaseline()).isNotNull();
        assertThat(defaults.moodBaseline().pleasure()).isEqualTo(0.0);
        assertThat(defaults.moodBaseline().arousal()).isEqualTo(-0.2);
        assertThat(defaults.curiosity()).isNotNull();
        assertThat(defaults.curiosity().proximityScale()).isEqualTo(7.0);
        assertThat(defaults.temporalFocus()).isNotNull();
        assertThat(defaults.temporalFocus().worseningBoostCap()).isEqualTo(1.0);
        assertThat(defaults.vocabulary()).isNotNull();
        assertThat(defaults.vocabulary().edgeTypes()).hasSize(2);
        assertThat(defaults.vocabulary().edgeTypes().get(0).canonical()).isEqualTo("knows");
        assertThat(defaults.services()).containsEntry("reflectionSynthesizer", "llm");
    }

    @Test
    void minimalProfile_onlyAgentId() throws IOException {
        var defaults = parse("cognitive-profiles/minimal.yaml");

        assertThat(defaults.agentId()).isEqualTo("bob");
        assertThat(defaults.tenantId()).isNull();
        assertThat(defaults.personality()).isNull();
        assertThat(defaults.moodBaseline()).isNull();
        assertThat(defaults.curiosity()).isNull();
        assertThat(defaults.temporalFocus()).isNull();
        assertThat(defaults.vocabulary()).isNull();
        assertThat(defaults.services()).isEmpty();
    }

    @Test
    void missingAgentId_throwsNullPointer() {
        assertThatThrownBy(() -> CognitiveDefaults.empty(null))
            .isInstanceOf(NullPointerException.class)
            .hasMessageContaining("agentId");
    }

    @Test
    void servicesMap_isDefensivelyCopied() throws IOException {
        var defaults = parse("cognitive-profiles/alice.yaml");
        assertThatThrownBy(() -> defaults.services().put("new", "value"))
            .isInstanceOf(UnsupportedOperationException.class);
    }
}

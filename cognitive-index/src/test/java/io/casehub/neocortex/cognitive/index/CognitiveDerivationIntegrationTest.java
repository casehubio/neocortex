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

import static org.assertj.core.api.Assertions.assertThat;

class CognitiveDerivationIntegrationTest {

    private ObjectMapper mapper;

    @BeforeEach
    void setUp() {
        mapper = CognitiveDefaultsRegistry.createMapper();
    }

    @Test
    void deriveFrom_producesPersonalityFromDescriptor() throws IOException {
        String yaml = """
            agentId: analyst-01
            descriptor:
              agentId: analyst-01
              disposition:
                socialOrient: independent
                ruleFollowing: moderate
                riskAppetite: calculated
                autonomy: high
                conflictMode: analytical
              dispositionProfile:
                - term: ni
                  weight: 0.35
                - term: te
                  weight: 0.30
                - term: fi
                  weight: 0.20
                - term: se
                  weight: 0.15
              goals:
                - identify strategic patterns
            """;

        CognitiveDefaults defaults = mapper.readValue(yaml, CognitiveDefaults.class);
        assertThat(defaults.descriptor()).isNotNull();
        assertThat(defaults.descriptor().agentId()).isEqualTo("analyst-01");

        CognitiveDefaults derived = CognitiveDerivationEngine.deriveAndMerge(defaults);
        assertThat(derived.personality()).isNotNull();
        assertThat(derived.personality().getWeight(new MemoryDomain("reflection")))
            .isGreaterThan(1.0);
        assertThat(derived.moodBaseline()).isNotNull();
        assertThat(derived.moodBaseline().dominance()).isGreaterThan(0.2);
    }

    @Test
    void deriveAndMerge_explicitOverridesTakePrecedence() throws IOException {
        String yaml = """
            agentId: custom-agent
            descriptor:
              agentId: custom-agent
              dispositionProfile:
                - term: ni
                  weight: 0.6
                - term: te
                  weight: 0.4
            personality:
              reflection: 2.0
            """;

        CognitiveDefaults defaults = mapper.readValue(yaml, CognitiveDefaults.class);
        CognitiveDefaults derived = CognitiveDerivationEngine.deriveAndMerge(defaults);

        assertThat(derived.personality().getWeight(new MemoryDomain("reflection")))
            .isEqualTo(2.0);
    }

    @Test
    void noDescriptor_returnsUnchanged() {
        var defaults = CognitiveDefaults.empty("agent");

        CognitiveDefaults result = CognitiveDerivationEngine.deriveAndMerge(defaults);
        assertThat(result).isSameAs(defaults);
    }
}

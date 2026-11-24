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
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class BriefingConfigTest {

    @Test
    void parsesFromYaml() throws Exception {
        var yaml = """
                identity: You are a stoic mentor.
                grounding: Draw on your memories.
                echo: Think as a stoic would.
                """;
        var mapper = new ObjectMapper(new YAMLFactory());
        var config = mapper.readValue(yaml, BriefingConfig.class);
        assertEquals("You are a stoic mentor.", config.identity());
        assertEquals("Draw on your memories.", config.grounding());
        assertEquals("Think as a stoic would.", config.echo());
    }

    @Test
    void allFieldsNullable() throws Exception {
        var yaml = "{}";
        var mapper = new ObjectMapper(new YAMLFactory());
        var config = mapper.readValue(yaml, BriefingConfig.class);
        assertNull(config.identity());
        assertNull(config.grounding());
        assertNull(config.echo());
    }

    @Test
    void cognitiveDefaultsCarriesBriefing() throws Exception {
        var yaml = """
                agentId: test-agent
                briefing:
                  identity: You are a test persona.
                  echo: Think as a test would.
                """;
        var mapper = new ObjectMapper(new YAMLFactory());
        var defaults = mapper.readValue(yaml, CognitiveDefaults.class);
        assertNotNull(defaults.briefing());
        assertEquals("You are a test persona.", defaults.briefing().identity());
        assertEquals("Think as a test would.", defaults.briefing().echo());
    }

    @Test
    void cognitiveDefaultsBriefingNullWhenAbsent() {
        var defaults = CognitiveDefaults.empty("test");
        assertNull(defaults.briefing());
    }

    @Test
    void withBriefingPreservesBriefing() {
        var config = new BriefingConfig("identity", "grounding", "echo");
        var defaults = CognitiveDefaults.empty("test").withBriefing(config);
        assertNotNull(defaults.briefing());
        assertEquals("identity", defaults.briefing().identity());
    }

    @Test
    void withTenantIdPreservesBriefing() {
        var config = new BriefingConfig("identity", null, "echo");
        var defaults = CognitiveDefaults.empty("test").withBriefing(config).withTenantId("t1");
        assertNotNull(defaults.briefing());
        assertEquals("identity", defaults.briefing().identity());
    }
}

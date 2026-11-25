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
import io.casehub.neocortex.cognitive.index.CognitiveDefaults;
import org.junit.jupiter.api.Test;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.*;

class PersonaProfileTest {

    private static CognitiveDefaults loadProfile(String name) throws IOException {
        var mapper = new ObjectMapper(new YAMLFactory())
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        try (var is = PersonaProfileTest.class.getClassLoader()
                .getResourceAsStream("cognitive-profiles/" + name + ".yaml")) {
            assertNotNull(is, "Profile " + name + " should be on classpath");
            return mapper.readValue(is, CognitiveDefaults.class);
        }
    }

    @Test
    void stoicMentorLoads() throws IOException {
        var profile = loadProfile("stoic-mentor");
        assertEquals("stoic-mentor", profile.agentId());
    }

    @Test
    void stoicMentorHasBriefing() throws IOException {
        var profile = loadProfile("stoic-mentor");
        assertNotNull(profile.briefing());
        assertTrue(profile.briefing().identity().contains("stoic mentor"));
        assertTrue(profile.briefing().echo().contains("duty"));
    }

    @Test
    void boldStrategistLoads() throws IOException {
        var profile = loadProfile("bold-strategist");
        assertEquals("bold-strategist", profile.agentId());
    }

    @Test
    void boldStrategistHasBriefing() throws IOException {
        var profile = loadProfile("bold-strategist");
        assertNotNull(profile.briefing());
        assertTrue(profile.briefing().identity().contains("bold strategist"));
        assertTrue(profile.briefing().echo().contains("ambitious"));
    }

    @Test
    void profilesHaveBehavioralDefaults() throws IOException {
        var stoic = loadProfile("stoic-mentor");
        assertNotNull(stoic.curiosity());
        assertEquals(5.0, stoic.curiosity().proximityScale());

        var bold = loadProfile("bold-strategist");
        assertNotNull(bold.curiosity());
        assertEquals(9.0, bold.curiosity().proximityScale());
    }

    @Test
    void stoicMentorHasDisposition() throws IOException {
        var profile = loadProfile("stoic-mentor");
        assertNotNull(profile.descriptor(), "stoic-mentor should have a descriptor");
        var axes = profile.descriptor().disposition();
        assertNotNull(axes, "stoic-mentor should have disposition axes");
        assertEquals("high", axes.ruleFollowing());
        assertEquals("low", axes.riskAppetite());
        assertEquals("high", axes.autonomy());
        assertEquals("moderate", axes.socialOrient());
        assertEquals("collaborative", axes.conflictMode());
    }

    @Test
    void boldStrategistHasDisposition() throws IOException {
        var profile = loadProfile("bold-strategist");
        assertNotNull(profile.descriptor(), "bold-strategist should have a descriptor");
        var axes = profile.descriptor().disposition();
        assertNotNull(axes, "bold-strategist should have disposition axes");
        assertEquals("low", axes.ruleFollowing());
        assertEquals("high", axes.riskAppetite());
        assertEquals("high", axes.autonomy());
        assertEquals("high", axes.socialOrient());
        assertEquals("competitive", axes.conflictMode());
    }

    @Test
    void thoroughPlannerLoads() throws IOException {
        var profile = loadProfile("thorough-planner");
        assertEquals("thorough-planner", profile.agentId());
        assertNotNull(profile.briefing());
        assertTrue(profile.briefing().identity().contains("thorough planner"));
        assertTrue(profile.briefing().echo().contains("methodical"));
    }

    @Test
    void thoroughPlannerHasDisposition() throws IOException {
        var profile = loadProfile("thorough-planner");
        assertNotNull(profile.descriptor());
        var axes = profile.descriptor().disposition();
        assertNotNull(axes);
        assertEquals("high", axes.ruleFollowing());
        assertEquals("low", axes.riskAppetite());
        assertEquals("moderate", axes.autonomy());
        assertEquals("cooperative", axes.conflictMode());
    }

    @Test
    void pragmaticAssistantLoads() throws IOException {
        var profile = loadProfile("pragmatic-assistant");
        assertEquals("pragmatic-assistant", profile.agentId());
        assertNotNull(profile.briefing());
        assertTrue(profile.briefing().identity().contains("pragmatic assistant"));
        assertTrue(profile.briefing().echo().contains("actionable") || profile.briefing().echo().contains("achievable"));
    }

    @Test
    void pragmaticAssistantHasDisposition() throws IOException {
        var profile = loadProfile("pragmatic-assistant");
        assertNotNull(profile.descriptor());
        var axes = profile.descriptor().disposition();
        assertNotNull(axes);
        assertEquals("moderate", axes.ruleFollowing());
        assertEquals("moderate", axes.riskAppetite());
        assertEquals("high", axes.socialOrient());
        assertEquals("accommodating", axes.conflictMode());
    }

    @Test
    void contrastingCuriosityConfig() throws IOException {
        var stoic = loadProfile("stoic-mentor");
        var bold = loadProfile("bold-strategist");
        assertTrue(bold.curiosity().proximityScale() > stoic.curiosity().proximityScale(),
            "Bold strategist should have higher curiosity proximity than stoic mentor");
    }
}

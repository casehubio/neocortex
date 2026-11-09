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

import io.casehub.neocortex.cognitive.CognitiveCapacityConfig;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class CognitiveCapacityDerivationTest {

    @Test
    void compressed_range_bounds() {
        var analytical = buildProfile(0.8, 0.7, 0.6, 0.3, 0.5);
        var config = CognitiveDerivationEngine.deriveCapacity(analytical);
        assertNotNull(config);
        assertTrue(config.relationalComplexity() >= 0.55 && config.relationalComplexity() <= 0.95,
                "relational " + config.relationalComplexity() + " not in [0.55, 0.95]");
        assertTrue(config.associativeReach() >= 0.55 && config.associativeReach() <= 0.95,
                "associative " + config.associativeReach() + " not in [0.55, 0.95]");
        assertTrue(config.workingMemorySpan() >= 0.55 && config.workingMemorySpan() <= 0.95,
                "wmSpan " + config.workingMemorySpan() + " not in [0.55, 0.95]");
    }

    @Test
    void analytical_has_higher_relational_than_exploratory() {
        var analytical = buildProfile(0.8, 0.7, 0.6, 0.3, 0.5);
        var exploratory = buildProfile(0.2, 0.2, 0.5, 0.9, 0.3);
        var a = CognitiveDerivationEngine.deriveCapacity(analytical);
        var e = CognitiveDerivationEngine.deriveCapacity(exploratory);
        assertTrue(a.relationalComplexity() > e.relationalComplexity(),
                "Analytical (high Ti+Te) should have higher relational complexity");
    }

    @Test
    void exploratory_has_higher_associative_than_analytical() {
        var analytical = buildProfile(0.8, 0.7, 0.6, 0.3, 0.5);
        var exploratory = buildProfile(0.2, 0.2, 0.5, 0.9, 0.3);
        var a = CognitiveDerivationEngine.deriveCapacity(analytical);
        var e = CognitiveDerivationEngine.deriveCapacity(exploratory);
        assertTrue(e.associativeReach() > a.associativeReach(),
                "Exploratory (high Ne+Ni) should have higher associative reach");
    }

    @Test
    void null_profile_returns_null() {
        assertNull(CognitiveDerivationEngine.deriveCapacity(null));
        assertNull(CognitiveDerivationEngine.deriveCapacity(List.of()));
    }

    @Test
    void concrete_analytical_values() {
        var profile = buildProfile(0.8, 0.7, 0.6, 0.3, 0.5);
        var config = CognitiveDerivationEngine.deriveCapacity(profile);
        assertEquals(0.85, config.relationalComplexity(), 0.001);
        assertEquals(0.73, config.associativeReach(), 0.001);
        assertEquals(0.79, config.workingMemorySpan(), 0.001);
    }

    @Test
    void merge_explicit_wins_over_derived() {
        var explicit = new CognitiveCapacityConfig(0.9, null, null);
        var derived = new CognitiveCapacityConfig(0.7, 0.73, 0.79);
        var merged = CognitiveDerivationEngine.mergeCapacity(explicit, derived);
        assertEquals(0.9, merged.relationalComplexity());
        assertEquals(0.73, merged.associativeReach());
        assertEquals(0.79, merged.workingMemorySpan());
    }

    @Test
    void merge_null_explicit_returns_derived() {
        var derived = new CognitiveCapacityConfig(0.7, 0.73, 0.79);
        var merged = CognitiveDerivationEngine.mergeCapacity(null, derived);
        assertSame(derived, merged);
    }

    @Test
    void merge_null_derived_returns_explicit() {
        var explicit = new CognitiveCapacityConfig(0.9, null, null);
        var merged = CognitiveDerivationEngine.mergeCapacity(explicit, null);
        assertSame(explicit, merged);
    }

    private static List<WeightedTerm> buildProfile(
            double ti, double te, double ni, double ne, double si) {
        return List.of(
                new WeightedTerm("ti", ti), new WeightedTerm("te", te),
                new WeightedTerm("ni", ni), new WeightedTerm("ne", ne),
                new WeightedTerm("si", si));
    }
}

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
package io.casehub.neocortex.cognitive;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class CognitiveCapacityConfigTest {

    @Test
    void defaults_returns_population_midpoint() {
        var config = CognitiveCapacityConfig.defaults();
        assertEquals(0.75, config.relationalComplexity());
        assertEquals(0.75, config.associativeReach());
        assertEquals(0.75, config.workingMemorySpan());
    }

    @Test
    void validation_rejects_above_one() {
        assertThrows(IllegalArgumentException.class,
                () -> new CognitiveCapacityConfig(1.5, 0.5, 0.5));
    }

    @Test
    void validation_rejects_below_zero() {
        assertThrows(IllegalArgumentException.class,
                () -> new CognitiveCapacityConfig(0.5, -0.1, 0.5));
    }

    @Test
    void null_fields_are_allowed() {
        var config = new CognitiveCapacityConfig(0.9, null, null);
        assertEquals(0.9, config.relationalComplexity());
        assertNull(config.associativeReach());
        assertNull(config.workingMemorySpan());
    }

    @Test
    void orDefault_returns_one_for_null() {
        var config = new CognitiveCapacityConfig(0.9, null, null);
        assertEquals(0.9, config.relationalComplexityOrDefault());
        assertEquals(1.0, config.associativeReachOrDefault());
        assertEquals(1.0, config.workingMemorySpanOrDefault());
    }

    @Test
    void mergeWith_fills_nulls_from_derived() {
        var explicit = new CognitiveCapacityConfig(0.9, null, null);
        var derived = new CognitiveCapacityConfig(0.7, 0.73, 0.79);
        var merged = explicit.mergeWith(derived);
        assertEquals(0.9, merged.relationalComplexity());
        assertEquals(0.73, merged.associativeReach());
        assertEquals(0.79, merged.workingMemorySpan());
    }

    @Test
    void mergeWith_explicit_wins_over_derived() {
        var explicit = new CognitiveCapacityConfig(0.9, 0.6, 0.8);
        var derived = new CognitiveCapacityConfig(0.7, 0.73, 0.79);
        var merged = explicit.mergeWith(derived);
        assertEquals(0.9, merged.relationalComplexity());
        assertEquals(0.6, merged.associativeReach());
        assertEquals(0.8, merged.workingMemorySpan());
    }

    @Test
    void withX_builders_produce_correct_copies() {
        var config = CognitiveCapacityConfig.defaults();
        assertEquals(0.9, config.withRelationalComplexity(0.9).relationalComplexity());
        assertEquals(0.75, config.withRelationalComplexity(0.9).associativeReach());
        assertEquals(0.6, config.withAssociativeReach(0.6).associativeReach());
        assertEquals(0.4, config.withWorkingMemorySpan(0.4).workingMemorySpan());
    }
}

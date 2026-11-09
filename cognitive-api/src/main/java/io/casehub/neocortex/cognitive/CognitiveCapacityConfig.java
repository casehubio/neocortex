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

/**
 * Cognitive capacity ceilings bounding search budget parameters.
 * <p>
 * Three dimensions from the intelligence literature:
 * <ul>
 *   <li>Relational complexity (Halford 1998) → depth ceiling</li>
 *   <li>Associative reach (Mednick 1962) → laterality ceiling</li>
 *   <li>Working memory span (Engle &amp; Kane 2004) → breadth ceiling</li>
 * </ul>
 * Boxed {@link Double} fields distinguish "not specified" (null) from
 * "explicitly set to 0.0", enabling per-field merge in
 * {@code CognitiveDerivationEngine.deriveAndMerge()}.
 */
public record CognitiveCapacityConfig(
        Double relationalComplexity,
        Double associativeReach,
        Double workingMemorySpan
) {
    public CognitiveCapacityConfig {
        if (relationalComplexity != null) validate("relationalComplexity", relationalComplexity);
        if (associativeReach != null) validate("associativeReach", associativeReach);
        if (workingMemorySpan != null) validate("workingMemorySpan", workingMemorySpan);
    }

    public static CognitiveCapacityConfig defaults() {
        return new CognitiveCapacityConfig(0.75, 0.75, 0.75);
    }

    private static void validate(String name, double value) {
        if (value < 0.0 || value > 1.0)
            throw new IllegalArgumentException(name + " must be in [0,1], got " + value);
    }

    public CognitiveCapacityConfig mergeWith(CognitiveCapacityConfig derived) {
        return new CognitiveCapacityConfig(
                relationalComplexity != null ? relationalComplexity : derived.relationalComplexity(),
                associativeReach != null ? associativeReach : derived.associativeReach(),
                workingMemorySpan != null ? workingMemorySpan : derived.workingMemorySpan());
    }

    public double relationalComplexityOrDefault() {
        return relationalComplexity != null ? relationalComplexity : 1.0;
    }

    public double associativeReachOrDefault() {
        return associativeReach != null ? associativeReach : 1.0;
    }

    public double workingMemorySpanOrDefault() {
        return workingMemorySpan != null ? workingMemorySpan : 1.0;
    }

    public CognitiveCapacityConfig withRelationalComplexity(double v) {
        return new CognitiveCapacityConfig(v, associativeReach, workingMemorySpan);
    }

    public CognitiveCapacityConfig withAssociativeReach(double v) {
        return new CognitiveCapacityConfig(relationalComplexity, v, workingMemorySpan);
    }

    public CognitiveCapacityConfig withWorkingMemorySpan(double v) {
        return new CognitiveCapacityConfig(relationalComplexity, associativeReach, v);
    }
}

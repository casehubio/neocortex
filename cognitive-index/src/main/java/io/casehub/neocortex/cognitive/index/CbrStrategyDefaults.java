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

import io.casehub.neocortex.memory.cbr.RetrievalMode;

import java.util.Objects;

public record CbrStrategyDefaults(
    double minSimilarity,
    int temporalDecayDays,
    RetrievalMode retrievalMode
) {
    public CbrStrategyDefaults {
        if (minSimilarity < 0.0 || minSimilarity > 1.0)
            throw new IllegalArgumentException("minSimilarity must be in [0,1], got " + minSimilarity);
        if (temporalDecayDays <= 0)
            throw new IllegalArgumentException("temporalDecayDays must be positive, got " + temporalDecayDays);
        Objects.requireNonNull(retrievalMode, "retrievalMode required");
    }

    public static CbrStrategyDefaults defaults() {
        return new CbrStrategyDefaults(0.5, 90, RetrievalMode.HYBRID);
    }
}

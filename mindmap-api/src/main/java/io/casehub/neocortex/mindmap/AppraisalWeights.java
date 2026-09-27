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
package io.casehub.neocortex.mindmap;

public record AppraisalWeights(
    double urgencyWeight,
    double relationshipWeight,
    double fearOnsetThreshold
) {
    public static final AppraisalWeights NEUTRAL =
        new AppraisalWeights(1.0, 1.0, 1.0);

    public AppraisalWeights {
        if (urgencyWeight <= 0.0)
            throw new IllegalArgumentException("urgencyWeight must be positive");
        if (relationshipWeight <= 0.0)
            throw new IllegalArgumentException("relationshipWeight must be positive");
        if (fearOnsetThreshold <= 0.0)
            throw new IllegalArgumentException("fearOnsetThreshold must be positive");
    }
}

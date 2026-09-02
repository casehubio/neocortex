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

import java.util.Objects;

public record GraphStructureDefaults(
    InferenceStyle inferenceStyle,
    double connectiveBias
) {
    public enum InferenceStyle { CONNECTIVE, CATEGORICAL, BALANCED }

    public GraphStructureDefaults {
        Objects.requireNonNull(inferenceStyle, "inferenceStyle required");
        if (connectiveBias < 0.0 || connectiveBias > 1.0)
            throw new IllegalArgumentException("connectiveBias must be in [0,1], got " + connectiveBias);
    }

    public static GraphStructureDefaults defaults() {
        return new GraphStructureDefaults(InferenceStyle.BALANCED, 0.5);
    }
}

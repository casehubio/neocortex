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
package io.casehub.neocortex.caps.engine;

/**
 * @param allowance     CUSUM noise filter (k). Degree values below this are absorbed.
 * @param threshold     CUSUM firing threshold (h) for the fast channel.
 * @param decay         Temporal decay (λ) for the fast channel. 1.0 = no decay.
 * @param slowAllowance Noise filter for the slow (drift) channel.
 * @param slowThreshold Firing threshold for the slow channel.
 */
public record DegreeAccumulatorConfig(
        double allowance,
        double threshold,
        double decay,
        double slowAllowance,
        double slowThreshold
) {
    public static DegreeAccumulatorConfig defaults() {
        return new DegreeAccumulatorConfig(0.2, 1.0, 0.85, 0.15, 2.0);
    }
}

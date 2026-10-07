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

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class FormationPadSummaryTest {

    @Test
    void rejectsNegativeMemoryCount() {
        assertThatThrownBy(() -> new FormationPadSummary(0.5, 1.0, -1))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void allowsZeroMemoryCount() {
        var summary = new FormationPadSummary(0.0, 0.0, 0);
        assertThat(summary.memoryCount()).isZero();
    }

    @Test
    void storesValues() {
        var summary = new FormationPadSummary(3.6, 4.0, 5);
        assertThat(summary.dominanceWeightedReward()).isEqualTo(3.6);
        assertThat(summary.totalPositivePleasure()).isEqualTo(4.0);
        assertThat(summary.memoryCount()).isEqualTo(5);
    }
}

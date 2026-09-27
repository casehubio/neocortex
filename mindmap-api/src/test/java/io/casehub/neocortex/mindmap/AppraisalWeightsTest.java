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

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class AppraisalWeightsTest {

    @Test
    void neutralConstant_allOnes() {
        assertEquals(1.0, AppraisalWeights.NEUTRAL.urgencyWeight());
        assertEquals(1.0, AppraisalWeights.NEUTRAL.relationshipWeight());
        assertEquals(1.0, AppraisalWeights.NEUTRAL.fearOnsetThreshold());
    }

    @Test
    void validWeights_accepted() {
        var w = new AppraisalWeights(1.3, 0.7, 0.85);
        assertEquals(1.3, w.urgencyWeight());
        assertEquals(0.7, w.relationshipWeight());
        assertEquals(0.85, w.fearOnsetThreshold());
    }

    @Test
    void zeroUrgencyWeight_rejected() {
        assertThrows(IllegalArgumentException.class,
            () -> new AppraisalWeights(0.0, 1.0, 1.0));
    }

    @Test
    void negativeRelationshipWeight_rejected() {
        assertThrows(IllegalArgumentException.class,
            () -> new AppraisalWeights(1.0, -0.5, 1.0));
    }

    @Test
    void negativeFearOnsetThreshold_rejected() {
        assertThrows(IllegalArgumentException.class,
            () -> new AppraisalWeights(1.0, 1.0, -0.1));
    }
}

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
package io.casehub.neocortex.cognition.regulation;

import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class ShameContextTest {

    @Test
    void isCompounding_trueWhenThreeOrMore() {
        var ctx = new ShameContext(3, Instant.now(), 0.0, null, 0.3, 0.2);
        assertThat(ctx.isCompounding()).isTrue();
    }

    @Test
    void isCompounding_falseWhenLessThanThree() {
        var ctx = new ShameContext(2, Instant.now(), 0.0, null, 0.3, 0.2);
        assertThat(ctx.isCompounding()).isFalse();
    }

    @Test
    void isRelationallyCharged_trueWhenHighIntimacy() {
        var ctx = new ShameContext(0, null, 0.6, "mentor", 0.3, 0.2);
        assertThat(ctx.isRelationallyCharged()).isTrue();
    }

    @Test
    void isRelationallyCharged_falseWhenLowIntimacy() {
        var ctx = new ShameContext(0, null, 0.3, null, 0.3, 0.2);
        assertThat(ctx.isRelationallyCharged()).isFalse();
    }

    @Test
    void isSurfaceLevel_trueWhenHighGrandiositAndCallousness() {
        var ctx = new ShameContext(0, null, 0.0, null, 0.8, 0.8);
        assertThat(ctx.isSurfaceLevel()).isTrue();
    }

    @Test
    void isSurfaceLevel_falseWhenCallousnessBelowThreshold() {
        var ctx = new ShameContext(0, null, 0.0, null, 0.8, 0.6);
        assertThat(ctx.isSurfaceLevel()).isFalse();
    }
}

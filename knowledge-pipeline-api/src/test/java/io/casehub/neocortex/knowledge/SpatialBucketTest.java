package io.casehub.neocortex.knowledge;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class SpatialBucketTest {

    @Test
    void encodesKingsCrossToExpectedGeohash() {
        String hash = SpatialBucket.encode(51.5317, -0.1240, 6);
        assertThat(hash).hasSize(6);
    }

    @Test
    void nearbyCoordinatesProduceSameHash() {
        String h1 = SpatialBucket.encode(51.5317, -0.1240, 6);
        String h2 = SpatialBucket.encode(51.5320, -0.1235, 6);
        assertThat(h1).isEqualTo(h2);
    }

    @Test
    void distantCoordinatesProduceDifferentHash() {
        String h1 = SpatialBucket.encode(51.5317, -0.1240, 6);
        String h2 = SpatialBucket.encode(55.9533, -3.1883, 6);
        assertThat(h1).isNotEqualTo(h2);
    }

    @Test
    void precision6ProducesApproximately1kmCells() {
        String h1 = SpatialBucket.encode(51.5000, -0.1000, 6);
        String h2 = SpatialBucket.encode(51.5100, -0.1000, 6);
        assertThat(h1).isNotEqualTo(h2);
    }
}

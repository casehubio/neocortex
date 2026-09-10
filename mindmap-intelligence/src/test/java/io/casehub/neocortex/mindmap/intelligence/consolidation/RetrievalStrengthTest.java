package io.casehub.neocortex.mindmap.intelligence.consolidation;

import org.junit.jupiter.api.Test;
import java.time.Duration;
import java.time.Instant;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

class RetrievalStrengthTest {

    @Test
    void nullLastAccessed_returnsZero() {
        assertThat(RetrievalStrength.compute(null, 0, 30.0)).isEqualTo(0.0);
    }

    @Test
    void justAccessed_returnsOne() {
        assertThat(RetrievalStrength.compute(Instant.now(), 0, 30.0))
            .isEqualTo(1.0);
    }

    @Test
    void halfLifeDecay_zeroStorageStrength() {
        Instant thirtyDaysAgo = Instant.now().minus(Duration.ofDays(30));
        double result = RetrievalStrength.compute(thirtyDaysAgo, 0, 30.0);
        assertThat(result).isCloseTo(0.5, within(0.05));
    }

    @Test
    void highStorageStrength_slowsDecay() {
        Instant thirtyDaysAgo = Instant.now().minus(Duration.ofDays(30));
        double low = RetrievalStrength.compute(thirtyDaysAgo, 1, 30.0);
        double high = RetrievalStrength.compute(thirtyDaysAgo, 100, 30.0);
        assertThat(high).isGreaterThan(low);
    }
}

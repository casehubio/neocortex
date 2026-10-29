package io.casehub.neocortex.cognitive.index;

import io.casehub.neocortex.cognitive.Confidence;
import io.casehub.neocortex.cognitive.ModulationFactor;
import io.casehub.neocortex.memory.Memory;
import io.casehub.neocortex.memory.MemoryDomain;
import io.casehub.neocortex.memory.Subject;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.time.Instant;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class ImportanceModulationFactorTest {

    private static final Instant NOW = Instant.parse("2026-06-01T12:00:00Z");
    private final ModulationFactor<Memory> factor = new ImportanceModulationFactor();

    @Test
    void missingAttributeReturnsDefault() {
        var memory = memory(Map.of());
        assertThat(factor.apply(memory, ModulationProfiles.MEMORY)).isEqualTo(1.0);
    }

    @ParameterizedTest
    @CsvSource({"1, 0.1", "5, 0.5", "7, 0.7", "10, 1.0"})
    void validScoreNormalized(int score, double expected) {
        var memory = memory(Map.of("importance", String.valueOf(score)));
        assertThat(factor.apply(memory, ModulationProfiles.MEMORY)).isEqualTo(expected);
    }

    @Test
    void malformedAttributeReturnsDefault() {
        var memory = memory(Map.of("importance", "notanumber"));
        assertThat(factor.apply(memory, ModulationProfiles.MEMORY)).isEqualTo(1.0);
    }

    @Test
    void zeroScoreClampsToPointOne() {
        var memory = memory(Map.of("importance", "0"));
        assertThat(factor.apply(memory, ModulationProfiles.MEMORY)).isEqualTo(0.1);
    }

    @Test
    void highImportanceRankedAboveLow() {
        var important = memory(Map.of("importance", "9"));
        var trivial = memory(Map.of("importance", "2"));
        assertThat(factor.apply(important, ModulationProfiles.MEMORY))
            .isGreaterThan(factor.apply(trivial, ModulationProfiles.MEMORY));
    }

    private static Memory memory(Map<String, String> attributes) {
        return new Memory("m1", Subject.of("agent", "entity"), new MemoryDomain("experience"),
            "t1", null, "text", attributes, NOW,
            Confidence.unknown(1.0), null, null, null, null, null);
    }
}

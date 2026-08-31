package io.casehub.neocortex.cognitive.index;

import io.casehub.neocortex.memory.Memory;
import io.casehub.neocortex.memory.MemoryDomain;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class AffectTrajectoryAnalyzerTest {

    private static final MemoryDomain AFFECT = new MemoryDomain("affect");
    private static final String TENANT = "t1";
    private static final Instant T0 = Instant.parse("2026-01-01T00:00:00Z");

    @Test
    void analyze_improvingPleasure_trendImproving() {
        var memories = List.of(
            mem(T0, 0.2, 0.5, 0.5),
            mem(T0.plusSeconds(3600), 0.4, 0.5, 0.5),
            mem(T0.plusSeconds(7200), 0.6, 0.5, 0.5)
        );

        AffectTrajectory result = AffectTrajectoryAnalyzer.analyze(memories);

        assertThat(result.trend()).isEqualTo(TrendDirection.IMPROVING);
        assertThat(result.pleasureSlope()).isGreaterThan(0);
        assertThat(result.sampleCount()).isEqualTo(3);
    }

    @Test
    void analyze_worseningPleasure_trendWorsening() {
        var memories = List.of(
            mem(T0, 0.8, 0.5, 0.5),
            mem(T0.plusSeconds(3600), 0.5, 0.5, 0.5),
            mem(T0.plusSeconds(7200), 0.2, 0.5, 0.5)
        );

        AffectTrajectory result = AffectTrajectoryAnalyzer.analyze(memories);

        assertThat(result.trend()).isEqualTo(TrendDirection.WORSENING);
        assertThat(result.pleasureSlope()).isLessThan(0);
    }

    @Test
    void analyze_stablePleasure_trendStable() {
        var memories = List.of(
            mem(T0, 0.5, 0.5, 0.5),
            mem(T0.plusSeconds(3600), 0.5, 0.5, 0.5),
            mem(T0.plusSeconds(7200), 0.5, 0.5, 0.5)
        );

        AffectTrajectory result = AffectTrajectoryAnalyzer.analyze(memories);

        assertThat(result.trend()).isEqualTo(TrendDirection.STABLE);
        assertThat(result.pleasureSlope()).isEqualTo(0.0);
    }

    @Test
    void analyze_highArousalVolatility() {
        var memories = List.of(
            mem(T0, 0.5, 0.1, 0.5),
            mem(T0.plusSeconds(3600), 0.5, 0.9, 0.5),
            mem(T0.plusSeconds(7200), 0.5, 0.1, 0.5),
            mem(T0.plusSeconds(10800), 0.5, 0.9, 0.5)
        );

        AffectTrajectory result = AffectTrajectoryAnalyzer.analyze(memories);

        assertThat(result.arousalVolatility()).isGreaterThan(0.3);
    }

    @Test
    void analyze_lowArousalVolatility() {
        var memories = List.of(
            mem(T0, 0.5, 0.50, 0.5),
            mem(T0.plusSeconds(3600), 0.5, 0.51, 0.5),
            mem(T0.plusSeconds(7200), 0.5, 0.49, 0.5)
        );

        AffectTrajectory result = AffectTrajectoryAnalyzer.analyze(memories);

        assertThat(result.arousalVolatility()).isLessThan(0.02);
    }

    @Test
    void analyze_singleEntry_stable() {
        var memories = List.of(mem(T0, 0.5, 0.5, 0.5));

        AffectTrajectory result = AffectTrajectoryAnalyzer.analyze(memories);

        assertThat(result.trend()).isEqualTo(TrendDirection.STABLE);
        assertThat(result.pleasureSlope()).isEqualTo(0.0);
        assertThat(result.arousalVolatility()).isEqualTo(0.0);
        assertThat(result.sampleCount()).isEqualTo(1);
    }

    @Test
    void analyze_emptyList_stable() {
        AffectTrajectory result = AffectTrajectoryAnalyzer.analyze(List.of());

        assertThat(result.trend()).isEqualTo(TrendDirection.STABLE);
        assertThat(result.sampleCount()).isEqualTo(0);
    }

    @Test
    void analyze_dominanceSlope() {
        var memories = List.of(
            mem(T0, 0.5, 0.5, 0.2),
            mem(T0.plusSeconds(3600), 0.5, 0.5, 0.5),
            mem(T0.plusSeconds(7200), 0.5, 0.5, 0.8)
        );

        AffectTrajectory result = AffectTrajectoryAnalyzer.analyze(memories);

        assertThat(result.dominanceSlope()).isGreaterThan(0);
    }

    @Test
    void analyze_nullPadTreatedAsZero() {
        var memories = List.of(
            new Memory("m1", "n1", AFFECT, TENANT, null, "PAD update", Map.of(), T0, null, null, null, null),
            mem(T0.plusSeconds(3600), 0.5, 0.5, 0.5)
        );

        AffectTrajectory result = AffectTrajectoryAnalyzer.analyze(memories);

        assertThat(result.sampleCount()).isEqualTo(2);
        assertThat(result.trend()).isEqualTo(TrendDirection.IMPROVING);
    }

    private static Memory mem(Instant ts, double p, double a, double d) {
        return new Memory(
            "m-" + ts.getEpochSecond(), "node-1", AFFECT, TENANT, null,
            "PAD update", Map.of(), ts, null, p, a, d);
    }
}

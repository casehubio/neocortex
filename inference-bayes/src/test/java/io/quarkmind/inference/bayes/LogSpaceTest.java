package io.quarkmind.inference.bayes;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

/**
 * Refs #620 — log-space arithmetic.
 */
public class LogSpaceTest {

    @Test
    void logsumexp_singleValue_returnsItself() {
        double[] vals = {Math.log(0.5)};
        assertThat(LogSpace.logsumexp(vals, 1)).isCloseTo(Math.log(0.5), within(1e-15));
    }

    @Test
    void logsumexp_twoValues_returnsLogOfSum() {
        double[] vals = {Math.log(0.3), Math.log(0.7)};
        assertThat(Math.exp(LogSpace.logsumexp(vals, 2))).isCloseTo(1.0, within(1e-15));
    }

    @Test
    void logsumexp_withNegInfinity_ignoresZeroTerms() {
        double[] vals = {Math.log(0.5), LogSpace.LOG_ZERO, Math.log(0.3)};
        assertThat(Math.exp(LogSpace.logsumexp(vals, 3))).isCloseTo(0.8, within(1e-15));
    }

    @Test
    void logsumexp_allNegInfinity_returnsNegInfinity() {
        double[] vals = {LogSpace.LOG_ZERO, LogSpace.LOG_ZERO};
        assertThat(LogSpace.logsumexp(vals, 2)).isEqualTo(LogSpace.LOG_ZERO);
    }

    @Test
    void normalize_sumsToOne() {
        double[] logVals = {Math.log(2.0), Math.log(3.0), Math.log(5.0)};
        LogSpace.normalize(logVals);
        double sum = Math.exp(logVals[0]) + Math.exp(logVals[1]) + Math.exp(logVals[2]);
        assertThat(sum).isCloseTo(1.0, within(1e-15));
        assertThat(Math.exp(logVals[0])).isCloseTo(0.2, within(1e-15));
        assertThat(Math.exp(logVals[1])).isCloseTo(0.3, within(1e-15));
        assertThat(Math.exp(logVals[2])).isCloseTo(0.5, within(1e-15));
    }

    @Test
    void normalize_withZero_preservesZero() {
        double[] logVals = {Math.log(0.5), LogSpace.LOG_ZERO, Math.log(0.5)};
        LogSpace.normalize(logVals);
        assertThat(Math.exp(logVals[0])).isCloseTo(0.5, within(1e-15));
        assertThat(logVals[1]).isEqualTo(LogSpace.LOG_ZERO);
        assertThat(Math.exp(logVals[2])).isCloseTo(0.5, within(1e-15));
    }

    @Test
    void toLogSpace_roundTrips() {
        double[] probs = {0.3, 0.0, 0.7};
        double[] logProbs = probs.clone();
        LogSpace.toLogSpace(logProbs);
        assertThat(logProbs[1]).isEqualTo(LogSpace.LOG_ZERO);
        LogSpace.toProbSpace(logProbs);
        assertThat(logProbs[0]).isCloseTo(0.3, within(1e-15));
        assertThat(logProbs[1]).isEqualTo(0.0);
        assertThat(logProbs[2]).isCloseTo(0.7, within(1e-15));
    }

    @Test
    void toLogSpaceCopy_doesNotMutateInput() {
        double[] probs = {0.5, 0.5};
        double[] logProbs = LogSpace.toLogSpaceCopy(probs);
        assertThat(probs[0]).isEqualTo(0.5);
        assertThat(logProbs[0]).isCloseTo(Math.log(0.5), within(1e-15));
    }
}

package io.casehub.neocortex.cognition.eval;

import io.casehub.neocortex.cognition.appraisal.AppraisalResult;
import io.casehub.neocortex.cognition.eval.divergence.DivergenceFixtures;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

class CognitionTestHarnessAppraisalTest {

    @Test
    void appraisalEnabledPopulatesTraceAfterTick() {
        var harness = DivergenceFixtures.pairedBuilder()
                .withAppraisalEnabled(true)
                .build();

        harness.observe("open", DivergenceFixtures.TENANT, "test observation");
        harness.tick("open", DivergenceFixtures.TENANT);

        var trace = harness.currentTrace("open", DivergenceFixtures.TENANT);
        assertThat(trace).isPresent();
        assertThat(trace.get().appraisalResult())
                .as("appraisal result should be populated after tick")
                .isNotNull();
        assertThat(trace.get().appraisalResult().secDimensions())
                .as("SEC dimensions should be populated")
                .isNotEmpty();
    }

    @Test
    void appraisalEnabledAndMockThrows() {
        assertThrows(IllegalStateException.class, () ->
            DivergenceFixtures.pairedBuilder()
                    .withAppraisalEnabled(true)
                    .withAppraisalResult("open", AppraisalResult.empty())
                    .build());
    }
}

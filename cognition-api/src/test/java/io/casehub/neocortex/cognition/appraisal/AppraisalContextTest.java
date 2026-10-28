package io.casehub.neocortex.cognition.appraisal;

import io.casehub.neocortex.cognitive.index.PersonalityFacet;
import io.casehub.neocortex.cognitive.index.RelationalSchema;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.*;

class AppraisalContextTest {

    private static final PerceivedSituation SITUATION =
            new PerceivedSituation("test observation", Map.of());

    @Test
    void nineArgConstructor_acceptsRelationalFields() {
        var schema = new RelationalSchema("target-1", "prey", 0.1, 0.0, 0.9, null);
        var facets = List.of(new PersonalityFacet("Callousness", 0.9, null));
        var ctx = new AppraisalContext(SITUATION, List.of(), null, null,
                HabituationState.empty(), null, null, schema, facets);
        assertThat(ctx.targetSchema()).isEqualTo(schema);
        assertThat(ctx.personalityFacets()).hasSize(1);
    }

    @Test
    void sevenArgConstructor_relationalFieldsAreNull() {
        var ctx = new AppraisalContext(SITUATION, List.of(), null, null,
                HabituationState.empty(), null, null);
        assertThat(ctx.targetSchema()).isNull();
        assertThat(ctx.personalityFacets()).isNull();
    }

    @Test
    void sixArgConstructor_relationalFieldsAreNull() {
        var ctx = new AppraisalContext(SITUATION, List.of(), null, null,
                HabituationState.empty(), null);
        assertThat(ctx.targetSchema()).isNull();
        assertThat(ctx.personalityFacets()).isNull();
    }
}

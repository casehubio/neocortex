package io.casehub.neocortex.cognition.appraisal;

import io.casehub.neocortex.caps.SituationActivation;
import io.casehub.neocortex.cognitive.index.RelationalSchema;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

class RelationalBiasingTest {

    @Test
    void returnsUnchangedWhenSchemaNull() {
        var activations = List.of(
                new SituationActivation("secure_attachment", 0.8),
                new SituationActivation("mastery", 0.5));

        var result = RelationalBiasing.apply(null, activations);

        assertThat(result).isEqualTo(activations);
    }

    @Test
    void returnsUnchangedWhenActivationsEmpty() {
        var schema = new RelationalSchema("target1", "ally", 0.7, 0.5, 0.3, null);

        var result = RelationalBiasing.apply(schema, List.of());

        assertThat(result).isEmpty();
    }

    @Test
    void highTrustBoostsSecureAttachment() {
        var schema = new RelationalSchema("target1", "ally", 0.8, 0.5, 0.3, null);
        var activations = List.of(new SituationActivation("secure_attachment", 0.6));

        var result = RelationalBiasing.apply(schema, activations);

        // 0.6 * (1.0 + 0.8 * 0.3) = 0.6 * 1.24 = 0.744
        assertThat(result).hasSize(1);
        assertThat(result.getFirst().confidence()).isCloseTo(0.744, within(0.001));
    }

    @Test
    void highTrustDampensRejection() {
        var schema = new RelationalSchema("target1", "ally", 0.8, 0.5, 0.3, null);
        var activations = List.of(new SituationActivation("rejection", 0.6));

        var result = RelationalBiasing.apply(schema, activations);

        // 0.6 * (1.0 - 0.8 * 0.2) = 0.6 * 0.84 = 0.504
        assertThat(result).hasSize(1);
        assertThat(result.getFirst().confidence()).isCloseTo(0.504, within(0.001));
    }

    @Test
    void highTrustDampensBetrayal() {
        var schema = new RelationalSchema("target1", "ally", 0.8, 0.5, 0.3, null);
        var activations = List.of(new SituationActivation("betrayal", 0.6));

        var result = RelationalBiasing.apply(schema, activations);

        // 0.6 * (1.0 - 0.8 * 0.25) = 0.6 * 0.8 = 0.48
        assertThat(result).hasSize(1);
        assertThat(result.getFirst().confidence()).isCloseTo(0.48, within(0.001));
    }

    @Test
    void highTrustDampensPsychologicalThreat() {
        var schema = new RelationalSchema("target1", "ally", 0.8, 0.5, 0.3, null);
        var activations = List.of(new SituationActivation("psychological_threat", 0.6));

        var result = RelationalBiasing.apply(schema, activations);

        // 0.6 * (1.0 - 0.8 * 0.15) = 0.6 * 0.88 = 0.528
        assertThat(result).hasSize(1);
        assertThat(result.getFirst().confidence()).isCloseTo(0.528, within(0.001));
    }

    @Test
    void negativeTrustBoostsThreatDampensSecure() {
        var schema = new RelationalSchema("target1", "threat", -0.7, 0.0, 0.0, null);
        var activations = List.of(
                new SituationActivation("secure_attachment", 0.6),
                new SituationActivation("rejection", 0.6));

        var result = RelationalBiasing.apply(schema, activations);

        // secure: 0.6 * (1.0 + (-0.7) * 0.3) = 0.6 * 0.79 = 0.474
        assertThat(result.get(0).nodeId()).isEqualTo("secure_attachment");
        assertThat(result.get(0).confidence()).isCloseTo(0.474, within(0.001));
        // rejection: 0.6 * (1.0 - (-0.7) * 0.2) = 0.6 * 1.14 = 0.684
        assertThat(result.get(1).nodeId()).isEqualTo("rejection");
        assertThat(result.get(1).confidence()).isCloseTo(0.684, within(0.001));
    }

    @Test
    void unaffectedNodesPassThrough() {
        var schema = new RelationalSchema("target1", "ally", 0.8, 0.5, 0.3, null);
        var activations = List.of(
                new SituationActivation("mastery", 0.5),
                new SituationActivation("dominance", 0.4));

        var result = RelationalBiasing.apply(schema, activations);

        assertThat(result).hasSize(2);
        assertThat(result.get(0).confidence()).isEqualTo(0.5);
        assertThat(result.get(1).confidence()).isEqualTo(0.4);
    }

    @Test
    void preservesSourceArray() {
        var schema = new RelationalSchema("target1", "ally", 0.8, 0.5, 0.3, null);
        int[] source = {1, 2, 3};
        var activations = List.of(new SituationActivation("secure_attachment", 0.6, source));

        var result = RelationalBiasing.apply(schema, activations);

        assertThat(result.getFirst().source()).isSameAs(source);
    }

    @Test
    void zeroTrustNoEffect() {
        var schema = new RelationalSchema("target1", "stranger", 0.0, 0.0, 0.0, null);
        var activations = List.of(
                new SituationActivation("secure_attachment", 0.6),
                new SituationActivation("rejection", 0.5));

        var result = RelationalBiasing.apply(schema, activations);

        assertThat(result.get(0).confidence()).isEqualTo(0.6);
        assertThat(result.get(1).confidence()).isEqualTo(0.5);
    }

    @Test
    void mixedActivationsOnlyBiasesTargeted() {
        var schema = new RelationalSchema("target1", "ally", 0.8, 0.5, 0.3, null);
        var activations = List.of(
                new SituationActivation("secure_attachment", 0.6),
                new SituationActivation("mastery", 0.5),
                new SituationActivation("betrayal", 0.4),
                new SituationActivation("dominance", 0.3));

        var result = RelationalBiasing.apply(schema, activations);

        assertThat(result).hasSize(4);
        assertThat(result.get(0).confidence()).isNotEqualTo(0.6);
        assertThat(result.get(1).confidence()).isEqualTo(0.5);
        assertThat(result.get(2).confidence()).isNotEqualTo(0.4);
        assertThat(result.get(3).confidence()).isEqualTo(0.3);
    }
}

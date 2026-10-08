package io.casehub.neocortex.cognition.subthought;

import io.casehub.neocortex.cognition.drive.DriveAxis;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SubThoughtModulationTest {

    @Test
    void concernBoostsAffiliation() {
        var result = new SubThoughtResult(List.of(
            new SubThought("concern", "worried", null, 0.5, SubThought.Source.SYNC)
        ), "hash");
        var mod = SubThoughtModulation.compute(result);
        assertTrue(mod.get(DriveAxis.AFFILIATION) > 0.0);
    }

    @Test
    void affectObservationBoostsAffiliation() {
        var result = new SubThoughtResult(List.of(
            new SubThought("affect-observation", "she seemed happy", null, 0.5, SubThought.Source.SYNC)
        ), "hash");
        var mod = SubThoughtModulation.compute(result);
        assertTrue(mod.get(DriveAxis.AFFILIATION) > 0.0);
    }

    @Test
    void intentionBoostsCompetenceAndAutonomy() {
        var result = new SubThoughtResult(List.of(
            new SubThought("intention", "should do it", null, 0.5, SubThought.Source.SYNC)
        ), "hash");
        var mod = SubThoughtModulation.compute(result);
        assertTrue(mod.get(DriveAxis.COMPETENCE) > 0.0);
        assertTrue(mod.get(DriveAxis.AUTONOMY) > 0.0);
    }

    @Test
    void associationBoostsCuriosity() {
        var result = new SubThoughtResult(List.of(
            new SubThought("association", "reminds me", null, 0.5, SubThought.Source.SYNC)
        ), "hash");
        var mod = SubThoughtModulation.compute(result);
        assertTrue(mod.get(DriveAxis.CURIOSITY) > 0.0);
    }

    @Test
    void causalInferenceBoostsCuriosity() {
        var result = new SubThoughtResult(List.of(
            new SubThought("causal-inference", "because of the rain", null, 0.5, SubThought.Source.SYNC)
        ), "hash");
        var mod = SubThoughtModulation.compute(result);
        assertTrue(mod.get(DriveAxis.CURIOSITY) > 0.0);
    }

    @Test
    void intensityCapsAtOne() {
        var many = new ArrayList<SubThought>();
        for (int i = 0; i < 20; i++) {
            many.add(new SubThought("concern", "worried " + i, null, 0.5, SubThought.Source.SYNC));
        }
        var mod = SubThoughtModulation.compute(new SubThoughtResult(many, "hash"));
        assertEquals(1.0, mod.get(DriveAxis.AFFILIATION));
    }

    @Test
    void emptyResultReturnsZeros() {
        var mod = SubThoughtModulation.compute(SubThoughtResult.EMPTY);
        assertEquals(0.0, mod.get(DriveAxis.AFFILIATION));
        assertEquals(0.0, mod.get(DriveAxis.CURIOSITY));
        assertEquals(0.0, mod.get(DriveAxis.COMPETENCE));
        assertEquals(0.0, mod.get(DriveAxis.AUTONOMY));
    }

    @Test
    void stepSizeIs015() {
        var result = new SubThoughtResult(List.of(
                new SubThought("concern", "worried", null, 0.5, SubThought.Source.SYNC)
                                                 ), "hash");
        var mod = SubThoughtModulation.compute(result);
// 0.5 confidence * 0.15 step = 0.075
        assertEquals(0.075, mod.get(DriveAxis.AFFILIATION), 0.001);
    }
}

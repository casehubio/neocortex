package io.quarkmind.inference.bayes;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class StructureResultTest {

    @Test
    void toBayesNetwork_fitsParametersFromData() {
        var spec = new NetworkSpec("test",
            List.of(
                new VariableSpec("A", List.of("a0", "a1"), null,
                    SpecStatus.PROPOSED, new CptSpec.Uniform()),
                new VariableSpec("B", List.of("b0", "b1"), null,
                    SpecStatus.PROPOSED, new CptSpec.Uniform())
            ),
            List.of(new EdgeSpec("A", "B", "learned", SpecStatus.PROPOSED)),
            null);

        var diagnostics = new StructureDiagnostics.PcDiagnostics(
            5, 0, 1, 1, 0, 1);
        var result = new StructureResult(spec, "PC", diagnostics);

        List<Map<String, String>> data = List.of(
            Map.of("A", "a0", "B", "b0"),
            Map.of("A", "a0", "B", "b0"),
            Map.of("A", "a0", "B", "b0"),
            Map.of("A", "a1", "B", "b1"),
            Map.of("A", "a1", "B", "b1"),
            Map.of("A", "a1", "B", "b1")
        );

        BayesNetwork bn = result.toBayesNetwork(data);
        assertNotNull(bn);
        assertEquals("test", bn.getName());
    }

    @Test
    void toBayesNetwork_withAlpha() {
        var spec = new NetworkSpec("test",
            List.of(
                new VariableSpec("X", List.of("x0", "x1"), null,
                    SpecStatus.PROPOSED, new CptSpec.Uniform())
            ),
            List.of(),
            null);

        var diagnostics = new StructureDiagnostics.PcDiagnostics(
            0, 0, 0, 0, 0, 0);
        var result = new StructureResult(spec, "PC", diagnostics);

        List<Map<String, String>> data = List.of(
            Map.of("X", "x0"),
            Map.of("X", "x1")
        );

        BayesNetwork bn = result.toBayesNetwork(data, 2.0);
        assertNotNull(bn);
    }

    @Test
    void networkSpec_returnsOriginalSpec() {
        var spec = new NetworkSpec("n", List.of(), List.of(), null);
        var diag = new StructureDiagnostics.PcDiagnostics(0, 0, 0, 0, 0, 0);
        var result = new StructureResult(spec, "PC", diag);
        assertSame(spec, result.networkSpec());
        assertEquals("PC", result.algorithm());
        assertSame(diag, result.diagnostics());
    }
}

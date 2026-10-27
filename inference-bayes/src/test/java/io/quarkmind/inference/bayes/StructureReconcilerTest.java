package io.quarkmind.inference.bayes;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StructureReconcilerTest {

    @Test
    void reconcile_allConfirmed() {
        var prior = makeSpec("prior",
            List.of("A", "B", "C"),
            List.of(edge("A", "B", "llm: causal"), edge("B", "C", "llm: causal")));
        var learned = makeSpec("learned",
            List.of("A", "B", "C"),
            List.of(edge("A", "B", "PC: oriented"), edge("B", "C", "PC: oriented")));

        ReconciliationResult result = StructureReconciler.reconcile(
            prior, learned, ConflictPolicy.TRUST_DATA);

        assertEquals(2, result.edges().size());
        assertFalse(result.hasConflicts());
        for (var e : result.edges()) {
            assertEquals(EdgeProvenance.DATA_CONFIRMED, e.provenance());
        }

        var merged = result.mergedSpec();
        assertEquals(3, merged.variables().size());
        assertEquals(2, merged.edges().size());
        for (var e : merged.edges()) {
            assertEquals(SpecStatus.ACCEPTED, e.status());
        }
    }

    @Test
    void reconcile_dataDiscoversExtraEdge() {
        var prior = makeSpec("prior",
            List.of("A", "B"),
            List.of(edge("A", "B", "llm")));
        var learned = makeSpec("learned",
            List.of("A", "B", "C"),
            List.of(edge("A", "B", "PC"), edge("B", "C", "PC: discovered")));

        ReconciliationResult result = StructureReconciler.reconcile(
            prior, learned, ConflictPolicy.TRUST_DATA);

        assertEquals(2, result.edges().size());
        assertFalse(result.hasConflicts());

        var ab = result.edges().stream()
            .filter(e -> e.from().equals("A") && e.to().equals("B")).findFirst().orElseThrow();
        assertEquals(EdgeProvenance.DATA_CONFIRMED, ab.provenance());

        var bc = result.edges().stream()
            .filter(e -> e.from().equals("B") && e.to().equals("C")).findFirst().orElseThrow();
        assertEquals(EdgeProvenance.DATA_DISCOVERED, bc.provenance());

        assertEquals(3, result.mergedSpec().variables().size());
    }

    @Test
    void reconcile_trustData_removesRejectedPriorEdge() {
        var prior = makeSpec("prior",
            List.of("A", "B", "C"),
            List.of(edge("A", "B", "llm"), edge("B", "C", "llm: dubious")));
        var learned = makeSpec("learned",
            List.of("A", "B", "C"),
            List.of(edge("A", "B", "PC")));

        ReconciliationResult result = StructureReconciler.reconcile(
            prior, learned, ConflictPolicy.TRUST_DATA);

        assertTrue(result.hasConflicts());
        assertEquals(1, result.conflicts().size());

        var conflict = result.conflicts().get(0);
        assertEquals("B", conflict.from());
        assertEquals("C", conflict.to());
        assertEquals(ConflictType.PRESENCE, conflict.type());

        assertEquals(1, result.mergedSpec().edges().size());
        assertEquals("A", result.mergedSpec().edges().get(0).from());
    }

    @Test
    void reconcile_trustData_directionConflict_usesDataDirection() {
        var prior = makeSpec("prior",
            List.of("A", "B"),
            List.of(edge("A", "B", "llm: A causes B")));
        var learned = makeSpec("learned",
            List.of("A", "B"),
            List.of(edge("B", "A", "PC: B causes A")));

        ReconciliationResult result = StructureReconciler.reconcile(
            prior, learned, ConflictPolicy.TRUST_DATA);

        assertTrue(result.hasConflicts());
        var conflict = result.conflicts().get(0);
        assertEquals(ConflictType.DIRECTION, conflict.type());

        var merged = result.mergedSpec();
        assertEquals(1, merged.edges().size());
        assertEquals("B", merged.edges().get(0).from());
        assertEquals("A", merged.edges().get(0).to());
    }

    @Test
    void reconcile_priorOnlyEdge_noConflict_whenNotInDataVariables() {
        var prior = makeSpec("prior",
            List.of("A", "B", "C"),
            List.of(edge("A", "B", "llm"), edge("B", "C", "llm: prior only")));
        var learned = makeSpec("learned",
            List.of("A", "B"),
            List.of(edge("A", "B", "PC")));

        ReconciliationResult result = StructureReconciler.reconcile(
            prior, learned, ConflictPolicy.TRUST_DATA);

        var bc = result.edges().stream()
            .filter(e -> e.from().equals("B") && e.to().equals("C")).findFirst().orElseThrow();
        assertEquals(EdgeProvenance.LLM_PROPOSED, bc.provenance());

        assertFalse(result.hasConflicts());
        assertEquals(2, result.mergedSpec().edges().size());
    }

    @Test
    void reconcile_trustPrior_keepsRejectedPriorEdge() {
        var prior = makeSpec("prior",
                             List.of("A", "B", "C"),
                             List.of(edge("A", "B", "llm"), edge("B", "C", "llm: dubious")));
        var learned = makeSpec("learned",
                               List.of("A", "B", "C"),
                               List.of(edge("A", "B", "PC")));

        ReconciliationResult result = StructureReconciler.reconcile(
                prior, learned, ConflictPolicy.TRUST_PRIOR);

        assertTrue(result.hasConflicts());
        assertEquals(2, result.mergedSpec().edges().size());
    }

    @Test
    void reconcile_trustPrior_directionConflict_usesPriorDirection() {
        var prior = makeSpec("prior",
                             List.of("A", "B"),
                             List.of(edge("A", "B", "llm")));
        var learned = makeSpec("learned",
                               List.of("A", "B"),
                               List.of(edge("B", "A", "PC")));

        ReconciliationResult result = StructureReconciler.reconcile(
                prior, learned, ConflictPolicy.TRUST_PRIOR);

        assertTrue(result.hasConflicts());
        assertEquals("A", result.mergedSpec().edges().get(0).from());
        assertEquals("B", result.mergedSpec().edges().get(0).to());
    }

    @Test
    void reconcile_flagForReview_presenceConflict_keepsAsProposed() {
        var prior = makeSpec("prior",
                             List.of("A", "B", "C"),
                             List.of(edge("A", "B", "llm"), edge("B", "C", "llm")));
        var learned = makeSpec("learned",
                               List.of("A", "B", "C"),
                               List.of(edge("A", "B", "PC")));

        ReconciliationResult result = StructureReconciler.reconcile(
                prior, learned, ConflictPolicy.FLAG_FOR_REVIEW);

        assertTrue(result.hasConflicts());
        assertEquals(2, result.mergedSpec().edges().size());

        var bc = result.mergedSpec().edges().stream()
                       .filter(e -> e.from().equals("B") && e.to().equals("C")).findFirst().orElseThrow();
        assertEquals(SpecStatus.PROPOSED, bc.status());
    }

    @Test
    void reconcile_flagForReview_directionConflict_keepsPriorDirectionAsProposed() {
        var prior = makeSpec("prior",
                             List.of("A", "B"),
                             List.of(edge("A", "B", "llm")));
        var learned = makeSpec("learned",
                               List.of("A", "B"),
                               List.of(edge("B", "A", "PC")));

        ReconciliationResult result = StructureReconciler.reconcile(
                prior, learned, ConflictPolicy.FLAG_FOR_REVIEW);

        assertTrue(result.hasConflicts());
        assertEquals("A", result.mergedSpec().edges().get(0).from());
        assertEquals(SpecStatus.PROPOSED, result.mergedSpec().edges().get(0).status());
    }

    @Test
    void reconcile_lockedPriorEdge_survivesEvenWhenDataDisagrees() {
        var prior = new NetworkSpec("prior",
                                    List.of(
                                            new VariableSpec("A", List.of("t", "f"), null, SpecStatus.PROPOSED, null),
                                            new VariableSpec("B", List.of("t", "f"), null, SpecStatus.PROPOSED, null)),
                                    List.of(new EdgeSpec("A", "B", "locked", SpecStatus.LOCKED)),
                                    null);
        var learned = makeSpec("learned",
                               List.of("A", "B"),
                               List.of());

        ReconciliationResult result = StructureReconciler.reconcile(
                prior, learned, ConflictPolicy.TRUST_DATA);

        assertFalse(result.hasConflicts());
        assertEquals(1, result.mergedSpec().edges().size());
        assertEquals(SpecStatus.LOCKED, result.mergedSpec().edges().get(0).status());

        var resolution = result.edges().get(0);
        assertEquals(EdgeProvenance.LLM_PROPOSED, resolution.provenance());
    }

    @Test
    void reconcile_variableUnion() {
        var prior = makeSpec("prior",
                             List.of("A", "B"),
                             List.of(edge("A", "B", "llm")));
        var learned = makeSpec("learned",
                               List.of("B", "C"),
                               List.of(edge("B", "C", "PC")));

        ReconciliationResult result = StructureReconciler.reconcile(
                prior, learned, ConflictPolicy.TRUST_DATA);

        assertEquals(3, result.mergedSpec().variables().size());
        var varNames = result.mergedSpec().variables().stream()
                             .map(VariableSpec::name).toList();
        assertTrue(varNames.contains("A"));
        assertTrue(varNames.contains("B"));
        assertTrue(varNames.contains("C"));
    }

    @Test
    void reconcile_emptyPrior_allDataDiscovered() {
        var prior = makeSpec("prior", List.of("A", "B"), List.of());
        var learned = makeSpec("learned",
                               List.of("A", "B"),
                               List.of(edge("A", "B", "PC")));

        ReconciliationResult result = StructureReconciler.reconcile(
                prior, learned, ConflictPolicy.TRUST_DATA);

        assertFalse(result.hasConflicts());
        assertEquals(1, result.edges().size());
        assertEquals(EdgeProvenance.DATA_DISCOVERED, result.edges().get(0).provenance());
    }

    @Test
    void reconcile_emptyLearned_allPriorOnly() {
        var prior = makeSpec("prior",
                             List.of("A", "B"),
                             List.of(edge("A", "B", "llm")));
        var learned = makeSpec("learned", List.of("C"), List.of());

        ReconciliationResult result = StructureReconciler.reconcile(
                prior, learned, ConflictPolicy.TRUST_DATA);

        assertFalse(result.hasConflicts());
        assertEquals(1, result.edges().size());
        assertEquals(EdgeProvenance.LLM_PROPOSED, result.edges().get(0).provenance());
    }


    static NetworkSpec makeSpec(String name, List<String> varNames, List<EdgeSpec> edges) {
        var vars = varNames.stream()
            .map(v -> new VariableSpec(v, List.of("t", "f"), null, SpecStatus.PROPOSED, null))
            .toList();
        return new NetworkSpec(name, vars, edges, null);
    }

    static EdgeSpec edge(String from, String to, String rationale) {
        return new EdgeSpec(from, to, rationale, SpecStatus.PROPOSED);
    }
}

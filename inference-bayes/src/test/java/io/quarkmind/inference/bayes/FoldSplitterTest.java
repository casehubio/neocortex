package io.quarkmind.inference.bayes;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class FoldSplitterTest {

    private static final List<Map<String, String>> DATA_10 = makeData(10);

    @Test
    void kFold_producesCorrectFoldCount() {
        var folds = FoldSplitter.kFold(5).split(DATA_10, 42L);
        assertThat(folds).hasSize(5);
    }

    @Test
    void kFold_noDataLoss() {
        var folds = FoldSplitter.kFold(5).split(DATA_10, 42L);
        var allTest = new ArrayList<Map<String, String>>();
        for (var fold : folds) allTest.addAll(fold.test());
        assertThat(new HashSet<>(allTest)).hasSize(10);
    }

    @Test
    void kFold_noOverlapBetweenTrainAndTest() {
        var folds = FoldSplitter.kFold(3).split(DATA_10, 42L);
        for (var fold : folds) {
            for (var testObs : fold.test()) {
                assertThat(fold.train()).doesNotContain(testObs);
            }
        }
    }

    @Test
    void kFold_deterministic() {
        var folds1 = FoldSplitter.kFold(3).split(DATA_10, 42L);
        var folds2 = FoldSplitter.kFold(3).split(DATA_10, 42L);
        for (int i = 0; i < folds1.size(); i++) {
            assertThat(folds1.get(i).test()).isEqualTo(folds2.get(i).test());
        }
    }

    @Test
    void kFold_rejectsKBelow2() {
        assertThatThrownBy(() -> FoldSplitter.kFold(1))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void kFold_rejectsDataSmallerThanK() {
        assertThatThrownBy(() -> FoldSplitter.kFold(5).split(makeData(3), 42L))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void stratified_preservesDistribution() {
        var data = List.of(
            Map.of("X", "a", "Y", "1"), Map.of("X", "a", "Y", "2"),
            Map.of("X", "a", "Y", "3"), Map.of("X", "a", "Y", "4"),
            Map.of("X", "b", "Y", "5"), Map.of("X", "b", "Y", "6"),
            Map.of("X", "b", "Y", "7"), Map.of("X", "b", "Y", "8")
        );
        var folds = FoldSplitter.stratified(2, "X").split(data, 42L);
        assertThat(folds).hasSize(2);
        for (var fold : folds) {
            long aCount = fold.test().stream()
                .filter(m -> "a".equals(m.get("X"))).count();
            long bCount = fold.test().stream()
                .filter(m -> "b".equals(m.get("X"))).count();
            assertThat(aCount).isEqualTo(bCount);
        }
    }

    @Test
    void stratified_noDataLoss() {
        var data = List.of(
            Map.of("X", "a", "Y", "1"), Map.of("X", "a", "Y", "2"),
            Map.of("X", "b", "Y", "3"), Map.of("X", "b", "Y", "4"),
            Map.of("X", "b", "Y", "5"), Map.of("X", "b", "Y", "6")
        );
        var folds = FoldSplitter.stratified(2, "X").split(data, 42L);
        var allTest = new ArrayList<Map<String, String>>();
        for (var fold : folds) allTest.addAll(fold.test());
        assertThat(allTest).hasSize(6);
    }

    @Test
    void holdOut_producesSingleFold() {
        var folds = FoldSplitter.holdOut(0.3).split(DATA_10, 42L);
        assertThat(folds).hasSize(1);
        assertThat(folds.get(0).train().size() + folds.get(0).test().size())
            .isEqualTo(10);
    }

    @Test
    void holdOut_rejectsOutOfRange() {
        assertThatThrownBy(() -> FoldSplitter.holdOut(0.0))
            .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> FoldSplitter.holdOut(1.0))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void holdOut_deterministic() {
        var folds1 = FoldSplitter.holdOut(0.2).split(DATA_10, 42L);
        var folds2 = FoldSplitter.holdOut(0.2).split(DATA_10, 42L);
        assertThat(folds1.get(0).test()).isEqualTo(folds2.get(0).test());
    }

    private static List<Map<String, String>> makeData(int n) {
        var result = new ArrayList<Map<String, String>>();
        for (int i = 0; i < n; i++) {
            result.add(Map.of("id", String.valueOf(i)));
        }
        return List.copyOf(result);
    }
}

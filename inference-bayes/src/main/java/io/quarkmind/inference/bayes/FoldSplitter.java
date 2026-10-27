package io.quarkmind.inference.bayes;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

@FunctionalInterface
public interface FoldSplitter {

    List<Fold> split(List<Map<String, String>> data, long seed);

    record Fold(List<Map<String, String>> train,
                List<Map<String, String>> test) {}

    static FoldSplitter kFold(int k) {
        if (k < 2) throw new IllegalArgumentException("k must be >= 2: " + k);
        return (data, seed) -> {
            if (data.size() < k) throw new IllegalArgumentException(
                "Data size " + data.size() + " < k=" + k);
            var shuffled = new ArrayList<>(data);
            Collections.shuffle(shuffled, new Random(seed));
            int foldSize = data.size() / k;
            var folds = new ArrayList<Fold>();
            for (int i = 0; i < k; i++) {
                int start = i * foldSize;
                int end = (i == k - 1) ? data.size() : start + foldSize;
                var test = List.copyOf(shuffled.subList(start, end));
                var train = new ArrayList<Map<String, String>>();
                train.addAll(shuffled.subList(0, start));
                train.addAll(shuffled.subList(end, shuffled.size()));
                folds.add(new Fold(List.copyOf(train), test));
            }
            return List.copyOf(folds);
        };
    }

    @SuppressWarnings("unchecked")
    static FoldSplitter stratified(int k, String variable) {
        if (k < 2) throw new IllegalArgumentException("k must be >= 2: " + k);
        if (variable == null) throw new IllegalArgumentException("Variable must not be null");
        return (data, seed) -> {
            var groups = new LinkedHashMap<String, List<Map<String, String>>>();
            for (var obs : data) {
                String val = obs.get(variable);
                if (val == null) continue;
                groups.computeIfAbsent(val, v -> new ArrayList<>()).add(obs);
            }
            var rng = new Random(seed);
            for (var group : groups.values()) {
                Collections.shuffle(group, rng);
            }
            var foldLists = new ArrayList[k];
            for (int i = 0; i < k; i++) foldLists[i] = new ArrayList<Map<String, String>>();
            for (var group : groups.values()) {
                for (int j = 0; j < group.size(); j++) {
                    foldLists[j % k].add(group.get(j));
                }
            }
            var folds = new ArrayList<Fold>();
            for (int i = 0; i < k; i++) {
                var test = List.copyOf((List<Map<String, String>>) foldLists[i]);
                var train = new ArrayList<Map<String, String>>();
                for (int j = 0; j < k; j++) {
                    if (j != i) train.addAll((List<Map<String, String>>) foldLists[j]);
                }
                folds.add(new Fold(List.copyOf(train), test));
            }
            return List.copyOf(folds);
        };
    }

    static FoldSplitter holdOut(double testFraction) {
        if (testFraction <= 0 || testFraction >= 1) throw new IllegalArgumentException(
            "testFraction must be in (0, 1): " + testFraction);
        return (data, seed) -> {
            var shuffled = new ArrayList<>(data);
            Collections.shuffle(shuffled, new Random(seed));
            int splitIdx = (int) Math.round(data.size() * (1 - testFraction));
            if (splitIdx == 0) splitIdx = 1;
            if (splitIdx == data.size()) splitIdx = data.size() - 1;
            var train = List.copyOf(shuffled.subList(0, splitIdx));
            var test = List.copyOf(shuffled.subList(splitIdx, shuffled.size()));
            return List.of(new Fold(train, test));
        };
    }
}

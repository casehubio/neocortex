package io.casehub.neocortex.rag;

import java.util.Arrays;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

final class MinHashIndex {

    private final int numHashes;
    private final long[] hashA;
    private final long[] hashB;
    private static final long LARGE_PRIME = 4294967311L;

    MinHashIndex(int numHashes) {
        if (numHashes <= 0) throw new IllegalArgumentException("numHashes must be positive");
        this.numHashes = numHashes;
        this.hashA = new long[numHashes];
        this.hashB = new long[numHashes];
        // Deterministic hash family — same instance produces repeatable signatures
        long seed = 0x9E3779B97F4A7C15L;
        for (int i = 0; i < numHashes; i++) {
            seed ^= seed >>> 30;
            seed *= 0xBF58476D1CE4E5B9L;
            hashA[i] = (seed & 0x7FFFFFFFFFFFFFFFL) + 1;
            seed ^= seed >>> 27;
            seed *= 0x94D049BB133111EBL;
            hashB[i] = seed & 0x7FFFFFFFFFFFFFFFL;
        }
    }

    long[] signature(Set<String> elements) {
        long[] sig = new long[numHashes];
        Arrays.fill(sig, Long.MAX_VALUE);
        for (String element : elements) {
            long hash = element.hashCode() & 0xFFFFFFFFL;
            for (int i = 0; i < numHashes; i++) {
                long h = (hashA[i] * hash + hashB[i]) % LARGE_PRIME;
                if (h < sig[i]) sig[i] = h;
            }
        }
        return sig;
    }

    double approximateJaccard(long[] sig1, long[] sig2) {
        int matches = 0;
        for (int i = 0; i < numHashes; i++) {
            if (sig1[i] == sig2[i]) matches++;
        }
        return (double) matches / numHashes;
    }

    List<CandidatePair> candidatePairs(Map<String, Set<String>> docSets, double threshold) {
        // Compute LSH band parameters: b bands of r rows, b*r = numHashes
        // Probability of candidate: 1 - (1 - t^r)^b ≈ 0.5 when t = threshold
        int bestB = 1, bestR = numHashes;
        double bestDiff = Double.MAX_VALUE;
        for (int b = 1; b <= numHashes; b++) {
            if (numHashes % b != 0) continue;
            int r = numHashes / b;
            double prob = 1.0 - Math.pow(1.0 - Math.pow(threshold, r), b);
            double diff = Math.abs(prob - 0.5);
            if (diff < bestDiff) {
                bestDiff = diff;
                bestB = b;
                bestR = r;
            }
        }

        // Compute signatures
        Map<String, long[]> signatures = new HashMap<>();
        for (var entry : docSets.entrySet()) {
            signatures.put(entry.getKey(), signature(entry.getValue()));
        }

        // LSH banding: hash each band, collect bucket collisions
        Set<CandidatePair> seen = new HashSet<>();
        List<CandidatePair> candidates = new ArrayList<>();
        List<String> keys = new ArrayList<>(docSets.keySet());

        for (int band = 0; band < bestB; band++) {
            Map<Long, List<String>> buckets = new HashMap<>();
            int offset = band * bestR;
            for (String key : keys) {
                long[] sig = signatures.get(key);
                long bandHash = bandHash(sig, offset, bestR);
                buckets.computeIfAbsent(bandHash, k -> new ArrayList<>()).add(key);
            }
            for (var bucket : buckets.values()) {
                if (bucket.size() < 2) continue;
                for (int i = 0; i < bucket.size(); i++) {
                    for (int j = i + 1; j < bucket.size(); j++) {
                        String a = bucket.get(i), b = bucket.get(j);
                        String smaller = a.compareTo(b) < 0 ? a : b;
                        String larger = a.compareTo(b) < 0 ? b : a;
                        CandidatePair pair = new CandidatePair(smaller, larger);
                        if (seen.add(pair)) {
                            candidates.add(pair);
                        }
                    }
                }
            }
        }
        return candidates;
    }

    private static long bandHash(long[] sig, int offset, int rows) {
        long h = 17;
        for (int i = offset; i < offset + rows; i++) {
            h = h * 31 + sig[i];
        }
        return h;
    }

    record CandidatePair(String a, String b) {}
}

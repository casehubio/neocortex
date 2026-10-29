package io.casehub.neocortex.memory.cbr;

import java.util.List;

public final class EditDistanceSimilarity {

    private EditDistanceSimilarity() {}

    public static EditDistanceResult compute(List<String> query, List<String> caseSeq) {
        return compute(query, caseSeq, null, null, null);
    }

    public static EditDistanceResult compute(List<String> query, List<String> caseSeq,
                                             java.util.Map<String, java.util.Map<String, Double>> substitutionSimilarities) {
        return compute(query, caseSeq, substitutionSimilarities, null, null);
    }

    public static EditDistanceResult compute(List<String> query, List<String> caseSeq,
                                             java.util.Map<String, java.util.Map<String, Double>> substitutionSimilarities,
                                             Double insertCost, Double deleteCost) {
        int    n      = query.size();
        int    m      = caseSeq.size();
        double effIns = insertCost != null ? insertCost : 1.0;
        double effDel = deleteCost != null ? deleteCost : 1.0;

        if (n == 0 && m == 0) {return new EditDistanceResult(1.0, List.of());}

        double[][] dp = new double[n + 1][m + 1];
        for (int i = 0; i <= n; i++) {dp[i][0] = i * effDel;}
        for (int j = 0; j <= m; j++) {dp[0][j] = j * effIns;}

        for (int i = 1; i <= n; i++) {
            for (int j = 1; j <= m; j++) {
                String qLabel = query.get(i - 1);
                String cLabel = caseSeq.get(j - 1);
                double subCost;
                if (qLabel.equals(cLabel)) {
                    subCost = 0.0;
                } else {
                    subCost = 1.0 - lookupSimilarity(qLabel, cLabel, substitutionSimilarities);
                }
                dp[i][j] = Math.min(dp[i - 1][j] + effDel,
                                    Math.min(dp[i][j - 1] + effIns, dp[i - 1][j - 1] + subCost));
            }
        }

        double editDistance = dp[n][m];
        double maxDist;
        if (1.0 <= effDel + effIns) {
            maxDist = Math.min(n, m) + Math.max(0, n - m) * effDel + Math.max(0, m - n) * effIns;
        } else {
            maxDist = n * effDel + m * effIns;
        }
        double score = maxDist > 0 ? Math.max(0.0, 1.0 - editDistance / maxDist) : 1.0;

        List<EditStep> path = backtrace(dp, query, caseSeq);
        return new EditDistanceResult(score, path);
    }


    private static List<EditStep> backtrace(double[][] dp, List<String> query, List<String> caseSeq) {
        var path = new java.util.ArrayList<EditStep>();
        int i    = query.size(), j = caseSeq.size();
        while (i > 0 || j > 0) {
            if (i > 0 && j > 0) {
                double diagCost = dp[i - 1][j - 1];
                double upCost   = dp[i - 1][j];
                double leftCost = dp[i][j - 1];
                if (diagCost <= upCost && diagCost <= leftCost) {
                    EditOp op = query.get(i - 1).equals(caseSeq.get(j - 1))
                                ? EditOp.MATCH : EditOp.SUBSTITUTE;
                    path.add(new EditStep(i - 1, j - 1, op));
                    i--;
                    j--;
                } else if (upCost <= leftCost) {
                    path.add(new EditStep(i - 1, -1, EditOp.DELETE));
                    i--;
                } else {
                    path.add(new EditStep(-1, j - 1, EditOp.INSERT));
                    j--;
                }
            } else if (i > 0) {
                path.add(new EditStep(i - 1, -1, EditOp.DELETE));
                i--;
            } else {
                path.add(new EditStep(-1, j - 1, EditOp.INSERT));
                j--;
            }
        }
        return List.copyOf(path.reversed());
    }

    private static double lookupSimilarity(String a, String b,
                                           java.util.Map<String, java.util.Map<String, Double>> substitutionSimilarities) {
        if (substitutionSimilarities == null || substitutionSimilarities.isEmpty()) {return 0.0;}
        return substitutionSimilarities
                       .getOrDefault(a, java.util.Map.of())
                       .getOrDefault(b, 0.0);
    }
}

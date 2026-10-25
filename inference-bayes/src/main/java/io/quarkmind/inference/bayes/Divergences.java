package io.quarkmind.inference.bayes;

public final class Divergences {

    private Divergences() {}

    public static double klDivergence(double[] p, double[] q) {
        double kl = 0.0;
        for (int i = 0; i < p.length; i++) {
            if (p[i] > 0.0 && q[i] > 0.0) {
                kl += p[i] * Math.log(p[i] / q[i]);
            } else if (p[i] > 0.0) {
                return Double.POSITIVE_INFINITY;
            }
        }
        return kl;
    }

    public static double totalVariation(double[] p, double[] q) {
        double sum = 0.0;
        for (int i = 0; i < p.length; i++) {
            sum += Math.abs(p[i] - q[i]);
        }
        return 0.5 * sum;
    }

    public static BeliefDivergence compute(
            String agentA, String agentB, String variable,
            double[] posteriorA, double[] posteriorB) {
        return new BeliefDivergence(
            agentA, agentB, variable,
            klDivergence(posteriorA, posteriorB),
            klDivergence(posteriorB, posteriorA),
            totalVariation(posteriorA, posteriorB));
    }
}

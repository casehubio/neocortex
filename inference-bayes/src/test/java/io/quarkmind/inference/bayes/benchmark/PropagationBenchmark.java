package io.quarkmind.inference.bayes.benchmark;

import io.quarkmind.inference.bayes.BayesInstance;
import io.quarkmind.inference.bayes.JunctionTree;
import org.openjdk.jmh.annotations.*;
import org.openjdk.jmh.infra.Blackhole;

import java.util.concurrent.TimeUnit;

@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MICROSECONDS)
@Warmup(iterations = 3, time = 1)
@Measurement(iterations = 5, time = 1)
@Fork(1)
@State(Scope.Thread)
public class PropagationBenchmark {

    @Param({"6", "12", "23"})
    int chainSize;

    private BayesInstance<?> phase1Instance;
    private BayesInstance<?> phase2Instance;
    private BayesInstance<?> phase3Instance;

    @Setup(Level.Trial)
    public void setup() {
        JunctionTree chainJt = BayesNetworkFixtures.buildChain(chainSize);

        phase1Instance = new BayesInstance<>(chainJt);
        phase1Instance.setLikelyhood(BayesNetworkFixtures.varName(0), new double[]{0.8, 0.2});
        phase1Instance.globalUpdate();

        phase2Instance = new BayesInstance<>(chainJt, true);
        phase2Instance.setLikelyhood(BayesNetworkFixtures.varName(0), new double[]{0.8, 0.2});
        phase2Instance.globalUpdate();

        phase3Instance = new BayesInstance<>(chainJt, true);
        phase3Instance.setLikelyhood(BayesNetworkFixtures.varName(0), new double[]{0.8, 0.2});
        phase3Instance.globalUpdate();
    }

    @Benchmark
    public void phase1_noChange(Blackhole bh) {
        phase1Instance.globalUpdate();
        bh.consume(phase1Instance.marginalize(BayesNetworkFixtures.varName(0)));
    }

    @Benchmark
    public void phase2_incrementalCollect(Blackhole bh) {
        double v = 0.5 + 0.3 * Math.sin(System.nanoTime());
        phase2Instance.setLikelyhood(BayesNetworkFixtures.varName(0), new double[]{v, 1.0 - v});
        phase2Instance.globalUpdate();
        bh.consume(phase2Instance.marginalize(BayesNetworkFixtures.varName(0)));
    }

    @Benchmark
    public void phase3_retraction(Blackhole bh) {
        double v = 0.5 + 0.3 * Math.sin(System.nanoTime());
        phase3Instance.setLikelyhood(BayesNetworkFixtures.varName(0), new double[]{v, 1.0 - v});
        phase3Instance.globalUpdate();
        bh.consume(phase3Instance.marginalize(BayesNetworkFixtures.varName(0)));
    }

    @Benchmark
    public void forced_fullRebuild(Blackhole bh) {
        double v = 0.5 + 0.3 * Math.sin(System.nanoTime());
        phase2Instance.setLikelyhood(BayesNetworkFixtures.varName(0), new double[]{v, 1.0 - v});
        phase2Instance.globalUpdateForced();
        bh.consume(phase2Instance.marginalize(BayesNetworkFixtures.varName(0)));
    }
}

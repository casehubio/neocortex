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
public class ParallelDistributeBenchmark {

    @Param({"5", "12", "23"})
    int starLeaves;

    private BayesInstance<?> sequential;
    private BayesInstance<?> parallel;

    @Setup(Level.Trial)
    public void setup() {
        JunctionTree jt = BayesNetworkFixtures.buildStar(starLeaves);

        sequential = new BayesInstance<>(jt);
        sequential.setParallelEnabled(false);

        parallel = new BayesInstance<>(jt);
        parallel.setParallelEnabled(true);
    }

    @Benchmark
    public void sequential_distribute(Blackhole bh) {
        double v = 0.5 + 0.3 * Math.sin(System.nanoTime());
        sequential.setLikelyhood(BayesNetworkFixtures.varName(0), new double[]{v, 1.0 - v});
        sequential.globalUpdateForced();
        bh.consume(sequential.marginalize(BayesNetworkFixtures.varName(0)));
    }

    @Benchmark
    public void parallel_distribute(Blackhole bh) {
        double v = 0.5 + 0.3 * Math.sin(System.nanoTime());
        parallel.setLikelyhood(BayesNetworkFixtures.varName(0), new double[]{v, 1.0 - v});
        parallel.globalUpdateForced();
        bh.consume(parallel.marginalize(BayesNetworkFixtures.varName(0)));
    }
}

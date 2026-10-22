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
@Fork(value = 1, jvmArgsAppend = {"-Xms256m", "-Xmx256m"})
@State(Scope.Thread)
public class AllocationBenchmark {

    private BayesInstance<?> pooledInstance;
    private JunctionTree chainJt;

    @Setup(Level.Trial)
    public void setup() {
        chainJt = BayesNetworkFixtures.buildChain(20);
        pooledInstance = new BayesInstance<>(chainJt, true);
        pooledInstance.setLikelyhood(BayesNetworkFixtures.varName(0), new double[]{0.8, 0.2});
        pooledInstance.globalUpdate();
    }

    @Benchmark
    public void pooled_retractionUpdate(Blackhole bh) {
        double v = 0.5 + 0.3 * Math.sin(System.nanoTime());
        pooledInstance.setLikelyhood(BayesNetworkFixtures.varName(0), new double[]{v, 1.0 - v});
        pooledInstance.globalUpdate();
        bh.consume(pooledInstance.marginalize(BayesNetworkFixtures.varName(0)));
    }

    @Benchmark
    public void unpooled_forcedUpdate(Blackhole bh) {
        BayesInstance<?> fresh = new BayesInstance<>(chainJt);
        double v = 0.5 + 0.3 * Math.sin(System.nanoTime());
        fresh.setLikelyhood(BayesNetworkFixtures.varName(0), new double[]{v, 1.0 - v});
        fresh.globalUpdateForced();
        bh.consume(fresh.marginalize(BayesNetworkFixtures.varName(0)));
    }
}

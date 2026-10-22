package io.quarkmind.inference.bayes.benchmark;

import io.quarkmind.inference.bayes.BayesInstance;
import io.quarkmind.inference.bayes.JunctionTree;
import io.quarkmind.inference.bayes.MapAssignment;
import org.openjdk.jmh.annotations.*;
import org.openjdk.jmh.infra.Blackhole;

import java.util.concurrent.TimeUnit;

@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MICROSECONDS)
@Warmup(iterations = 3, time = 1)
@Measurement(iterations = 5, time = 1)
@Fork(1)
@State(Scope.Thread)
public class InitAndMapBenchmark {

    private JunctionTree chainJt;
    private BayesInstance<?> mapInstance;

    @Setup(Level.Trial)
    public void setup() {
        chainJt = BayesNetworkFixtures.buildChain(20);
        mapInstance = new BayesInstance<>(chainJt);
        mapInstance.setLikelyhood(BayesNetworkFixtures.varName(0), new double[]{0.8, 0.2});
        mapInstance.globalUpdate();
    }

    @Benchmark
    public void eagerInit(Blackhole bh) {
        BayesInstance<?> instance = new BayesInstance<>(chainJt, false, false);
        bh.consume(instance);
    }

    @Benchmark
    public void lazyInit(Blackhole bh) {
        BayesInstance<?> instance = new BayesInstance<>(chainJt, false, true);
        bh.consume(instance);
    }

    @Benchmark
    public void marginalOnly(Blackhole bh) {
        mapInstance.globalUpdate();
        for (int i = 0; i < 20; i++) {
            bh.consume(mapInstance.marginalize(BayesNetworkFixtures.varName(i)));
        }
    }

    @Benchmark
    public void mapInference(Blackhole bh) {
        MapAssignment assignment = mapInstance.mapInference();
        bh.consume(assignment);
    }
}

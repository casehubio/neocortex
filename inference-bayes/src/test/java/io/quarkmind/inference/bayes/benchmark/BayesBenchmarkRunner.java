package io.quarkmind.inference.bayes.benchmark;

import org.junit.jupiter.api.Test;
import org.openjdk.jmh.results.format.ResultFormatType;
import org.openjdk.jmh.runner.Runner;
import org.openjdk.jmh.runner.options.Options;
import org.openjdk.jmh.runner.options.OptionsBuilder;

class BayesBenchmarkRunner {

    @Test
    void runBenchmarks() throws Exception {
        Options opt = new OptionsBuilder()
                .include("io.quarkmind.inference.bayes.benchmark.*Benchmark")
                .resultFormat(ResultFormatType.TEXT)
                .result("target/jmh-results.txt")
                .shouldDoGC(true)
                .addProfiler("gc")
                .build();

        new Runner(opt).run();
    }
}

package io.quarkmind.inference.bayes;

import java.util.List;

public sealed interface CptSpec {
    record DirectCpt(double[][] values) implements CptSpec {
        public DirectCpt {
            values = values.clone();
        }
    }

    record NoisyOr(List<ParentInfluence> parentInfluences, double leak) implements CptSpec {
        public NoisyOr {
            parentInfluences = List.copyOf(parentInfluences);
        }
    }

    record Uniform() implements CptSpec {}
}

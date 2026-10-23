package io.quarkmind.inference.bayes;

import java.util.List;

public sealed interface CptSpec {
    record DirectCpt(double[][] values) implements CptSpec {}
    record NoisyOr(List<ParentInfluence> parentInfluences, double leak) implements CptSpec {}
    record Uniform() implements CptSpec {}
}

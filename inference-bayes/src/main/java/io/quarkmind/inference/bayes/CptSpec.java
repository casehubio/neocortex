package io.quarkmind.inference.bayes;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;

import java.util.List;

@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "type")
@JsonSubTypes({
    @JsonSubTypes.Type(value = CptSpec.DirectCpt.class, name = "direct"),
    @JsonSubTypes.Type(value = CptSpec.NoisyOr.class, name = "noisy_or"),
    @JsonSubTypes.Type(value = CptSpec.Uniform.class, name = "uniform")
})
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

package io.quarkmind.inference.bayes;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class NetworkSpecSerializationTest {

    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void directCptRoundTrips() throws Exception {
        CptSpec original = new CptSpec.DirectCpt(new double[][]{{0.8, 0.2}, {0.3, 0.7}});
        String json = mapper.writeValueAsString(original);
        CptSpec deserialized = mapper.readValue(json, CptSpec.class);
        assertThat(deserialized).isInstanceOf(CptSpec.DirectCpt.class);
        assertThat(((CptSpec.DirectCpt) deserialized).values()).isDeepEqualTo(
            new double[][]{{0.8, 0.2}, {0.3, 0.7}});
    }

    @Test
    void uniformCptRoundTrips() throws Exception {
        CptSpec original = new CptSpec.Uniform();
        String json = mapper.writeValueAsString(original);
        CptSpec deserialized = mapper.readValue(json, CptSpec.class);
        assertThat(deserialized).isInstanceOf(CptSpec.Uniform.class);
    }

    @Test
    void noisyOrCptRoundTrips() throws Exception {
        CptSpec original = new CptSpec.NoisyOr(
            List.of(new ParentInfluence("Weather", 0.8)), 0.05);
        String json = mapper.writeValueAsString(original);
        CptSpec deserialized = mapper.readValue(json, CptSpec.class);
        assertThat(deserialized).isInstanceOf(CptSpec.NoisyOr.class);
        var noisyOr = (CptSpec.NoisyOr) deserialized;
        assertThat(noisyOr.leak()).isEqualTo(0.05);
        assertThat(noisyOr.parentInfluences()).hasSize(1);
        assertThat(noisyOr.parentInfluences().get(0).parentName()).isEqualTo("Weather");
    }

    @Test
    void fromBayesNetworkExtractsStructure() {
        BayesNetwork original = BayesNetworkBuilder.create()
            .variable("Strategy", "RUSH", "MACRO").prior(0.4, 0.6)
            .variable("detector", "HIGH", "MEDIUM", "LOW")
                .given("Strategy").cpt(0.8, 0.15, 0.05, 0.1, 0.2, 0.7)
            .build();

        NetworkSpec spec = NetworkSpec.fromBayesNetwork(original, "test", "");
        assertThat(spec.name()).isEqualTo("test");
        assertThat(spec.variables()).hasSize(2);
        assertThat(spec.edges()).hasSize(1);
        assertThat(spec.edges().get(0).from()).isEqualTo("Strategy");
        assertThat(spec.edges().get(0).to()).isEqualTo("detector");

        var strategyVar = spec.variables().stream()
            .filter(v -> v.name().equals("Strategy")).findFirst().orElseThrow();
        assertThat(strategyVar.outcomes()).containsExactly("RUSH", "MACRO");
        assertThat(strategyVar.cptSpec()).isInstanceOf(CptSpec.DirectCpt.class);
        var prior = ((CptSpec.DirectCpt) strategyVar.cptSpec()).values();
        assertThat(prior[0][0]).isCloseTo(0.4, org.assertj.core.data.Offset.offset(0.001));
        assertThat(prior[0][1]).isCloseTo(0.6, org.assertj.core.data.Offset.offset(0.001));
    }

    @Test
    void fromBayesNetworkRoundTripPreservesCpts() {
        BayesNetwork original = BayesNetworkBuilder.create()
            .variable("Strategy", "RUSH", "MACRO").prior(0.4, 0.6)
            .variable("det1", "HIGH", "MEDIUM", "LOW")
                .given("Strategy").cpt(0.8, 0.15, 0.05, 0.1, 0.2, 0.7)
            .variable("det2", "HIGH", "LOW")
                .given("Strategy").cpt(0.9, 0.1, 0.2, 0.8)
            .build();

        NetworkSpec spec = NetworkSpec.fromBayesNetwork(original, "test", "");
        BayesNetwork rebuilt = spec.toBayesNetwork();

        assertThat(rebuilt.size()).isEqualTo(original.size());

        for (var node : rebuilt) {
            BayesVariable<?> var = node.getContent();
            BayesVariable<?> origVar = findVar(original, var.getName());
            assertThat(var.getProbabilityTable()).isDeepEqualTo(origVar.getProbabilityTable());
        }
    }

    @Test
    void fullJsonRoundTripPreservesNetwork() throws Exception {
        BayesNetwork original = BayesNetworkBuilder.create()
            .variable("Strategy", "RUSH", "MACRO").prior(0.4, 0.6)
            .variable("det1", "HIGH", "MEDIUM", "LOW")
                .given("Strategy").cpt(0.8, 0.15, 0.05, 0.1, 0.2, 0.7)
            .build();

        NetworkSpec spec = NetworkSpec.fromBayesNetwork(original, "roundtrip", "desc");
        String json = mapper.writeValueAsString(spec);
        NetworkSpec deserialized = mapper.readValue(json, NetworkSpec.class);
        BayesNetwork rebuilt = deserialized.toBayesNetwork();

        assertThat(rebuilt.size()).isEqualTo(original.size());
        for (var node : rebuilt) {
            BayesVariable<?> var = node.getContent();
            BayesVariable<?> origVar = findVar(original, var.getName());
            assertThat(var.getProbabilityTable()).isDeepEqualTo(origVar.getProbabilityTable());
        }
    }

    private static BayesVariable<?> findVar(BayesNetwork net, String name) {
        for (var node : net) {
            if (node.getContent().getName().equals(name)) {
                return node.getContent();
            }
        }
        throw new IllegalArgumentException("Variable not found: " + name);
    }
}

package io.casehub.neocortex.cognitive.index;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class EffectiveCognitiveDefaultsProducerTest {

    private static DescriptorView view(String agentId) {
        return DescriptorView.of(agentId,
                new DispositionAxes("cooperative", "moderate", "calculated", "moderate", "analytical"),
                List.of(new WeightedTerm("fe", 0.8)), List.of());
    }

    @Test
    void createsWithoutOptionalSources() {
        var registry = new CognitiveDefaultsRegistry();

        var effective = EffectiveCognitiveDefaultsProducer.create(registry, null, null);

        assertThat(effective).isNotNull();
        assertThat(effective.forAgentOrDefaults("tenant-1", "unknown-agent")).isNotNull();
        assertThat(effective.agentIds("tenant-1")).isEmpty();
    }

    @Test
    void fallsBackToRegistryDescriptorsWhenNoViewSource() {
        var registry = CognitiveDefaultsRegistry.forTesting(
                CognitiveDefaults.empty("agent-1").withDescriptor(view("agent-1")));

        var effective = EffectiveCognitiveDefaultsProducer.create(registry, null, null);

        assertThat(effective.agentIds("tenant-1")).containsExactly("agent-1");
        assertThat(effective.forAgent("tenant-1", "agent-1").agentId()).isEqualTo("agent-1");
    }

    @Test
    void usesSuppliedViewSource() {
        var registry = new CognitiveDefaultsRegistry();
        DescriptorViewSource source = new DescriptorViewSource() {
            @Override
            public Optional<DescriptorView> find(String tenantId, String agentId) {
                return "agent-2".equals(agentId) ? Optional.of(view("agent-2")) : Optional.empty();
            }

            @Override
            public List<String> agentIds(String tenantId) {
                return List.of("agent-2");
            }
        };

        var effective = EffectiveCognitiveDefaultsProducer.create(registry, source, null);

        assertThat(effective.agentIds("tenant-1")).containsExactly("agent-2");
        assertThat(effective.forAgent("tenant-1", "agent-2").agentId()).isEqualTo("agent-2");
    }
}

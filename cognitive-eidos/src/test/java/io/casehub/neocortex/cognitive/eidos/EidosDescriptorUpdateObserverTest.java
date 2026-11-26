package io.casehub.neocortex.cognitive.eidos;

import io.casehub.eidos.api.AgentDescriptor;
import io.casehub.eidos.api.AgentDescriptorUpdated;
import io.casehub.neocortex.cognitive.index.CognitiveDefaultsRegistry;
import io.casehub.neocortex.cognitive.index.DescriptorView;
import io.casehub.neocortex.cognitive.index.DispositionAxes;
import io.casehub.neocortex.cognitive.index.EffectiveCognitiveDefaults;
import io.casehub.neocortex.cognitive.index.WeightedTerm;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;

class EidosDescriptorUpdateObserverTest {

    @Test
    void descriptorUpdate_invalidatesEffectiveCognitiveDefaults() {
        var registry = new CognitiveDefaultsRegistry();
        var view = DescriptorView.of("agent-1",
                new DispositionAxes("collaborative", "principled", "measured", "semi-autonomous", "compromising"),
                List.of(new WeightedTerm("fe", 0.8)), List.of());
        var viewSource = new StubDescriptorViewSource();
        viewSource.add("tenant-1", view);

        var effective = new EffectiveCognitiveDefaults(registry, viewSource, (t, a, now) -> java.util.Optional.empty());

        var before = effective.forAgent("tenant-1", "agent-1");
        assertNotNull(before);

        var updatedView = DescriptorView.of("agent-1",
                new DispositionAxes("independent", "principled", "measured", "semi-autonomous", "compromising"),
                List.of(new WeightedTerm("ti", 0.9)), List.of());
        viewSource.add("tenant-1", updatedView);

        var stillCached = effective.forAgent("tenant-1", "agent-1");
        assertSame(before, stillCached);

        var observer = new EidosDescriptorUpdateObserver();
        observer.effectiveDefaults = effective;

        var descriptor = AgentDescriptor.builder()
                .agentId("agent-1").name("Agent One").slot("test").tenancyId("tenant-1")
                .build();
        observer.onDescriptorUpdated(new AgentDescriptorUpdated(descriptor, descriptor));

        var afterInvalidation = effective.forAgent("tenant-1", "agent-1");
        assertNotSame(before, afterInvalidation);
    }

    private static class StubDescriptorViewSource implements io.casehub.neocortex.cognitive.index.DescriptorViewSource {
        private final java.util.Map<String, DescriptorView> views = new java.util.HashMap<>();

        void add(String tenantId, DescriptorView view) {
            views.put(tenantId + ":" + view.agentId(), view);
        }

        @Override
        public java.util.Optional<DescriptorView> find(String tenantId, String agentId) {
            return java.util.Optional.ofNullable(views.get(tenantId + ":" + agentId));
        }

        @Override
        public List<String> agentIds(String tenantId) {
            return views.entrySet().stream()
                    .filter(e -> e.getKey().startsWith(tenantId + ":"))
                    .map(e -> e.getValue().agentId())
                    .toList();
        }
    }
}

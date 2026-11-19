package io.casehub.neocortex.cognition.situational;

import io.casehub.neocortex.cognition.trace.PipelineTrace.SituationalPersonalitySnapshot;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Instance;
import jakarta.enterprise.inject.Produces;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

@ApplicationScoped
public class SituationalSnapshotAccessorProducer {

    @Inject
    Instance<PersonalityStateService> stateService;

    @Produces
    @Singleton
    SituationalSnapshotAccessor snapshotAccessor() {
        if (!stateService.isResolvable()) {
            return (agentId, tenantId) -> Optional.empty();
        }
        var service = stateService.get();
        return (agentId, tenantId) -> {
            var state = service.currentState(tenantId, agentId);
            if (state == null) return Optional.empty();

            var modeIntensities = new LinkedHashMap<String, Double>();
            for (var entry : state.intensities().entrySet()) {
                modeIntensities.put(entry.getKey().name(), entry.getValue());
            }

            var affWinner = state.winners().get(SituationalMode.Group.AFFECTIVE);
            var socWinner = state.winners().get(SituationalMode.Group.SOCIAL);

            var facetDisplacements = new LinkedHashMap<String, Double>();
            int publishedCount = 0;
            for (var entry : state.facets().entrySet()) {
                var fs = entry.getValue();
                double totalShift = fs.homeOffset() + fs.displacement() + fs.promoted();
                facetDisplacements.put(entry.getKey(), totalShift);
                if (Math.abs(totalShift) >= PersonalityStateService.EPSILON_PUBLISH) {
                    publishedCount++;
                }
            }

            return Optional.of(new SituationalPersonalitySnapshot(
                    Map.copyOf(modeIntensities),
                    affWinner != null ? affWinner.name() : null,
                    socWinner != null ? socWinner.name() : null,
                    Map.copyOf(facetDisplacements),
                    publishedCount));
        };
    }
}

package io.casehub.neocortex.mindmap.intelligence;

import io.casehub.neocortex.memory.experience.ExperienceRecorded;
import io.casehub.neocortex.memory.experience.FormativeExperience;
import io.casehub.neocortex.memory.experience.Observation;
import io.casehub.platform.api.identity.PrincipalId;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Event;
import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;

import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.ConcurrentHashMap;

@ApplicationScoped
public class SubThoughtExtractionObserver {

    @Inject
    Event<SubThoughtExtractionRequested> extractionEvent;

    private final ConcurrentHashMap<String, Instant> lastExtraction = new ConcurrentHashMap<>();
    private static final Duration COOLDOWN = Duration.ofSeconds(5);

    void onExperienceRecorded(@Observes ExperienceRecorded event) {
        if (!(event.event() instanceof Observation)
                && !(event.event() instanceof FormativeExperience)) {
            return;
        }

        var key = event.event().agentId() + ":" + event.event().tenantId();
        var now = Instant.now();
        var last = lastExtraction.get(key);
        if (last != null && Duration.between(last, now).compareTo(COOLDOWN) < 0) {
            return;
        }
        lastExtraction.put(key, now);

        extractionEvent.fireAsync(new SubThoughtExtractionRequested(
            event.memoryId(),
            event.event().tenantId(),
            event.event().description(),
            PrincipalId.agent(event.event().agentId())
        ));
    }
}

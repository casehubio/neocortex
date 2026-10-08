package io.casehub.neocortex.cognition.subthought;

import io.casehub.neocortex.cognition.core.CognitionTickContext;
import io.casehub.neocortex.cognition.core.CognitionTickParticipant;
import io.casehub.neocortex.cognition.core.SubjectResolver;
import io.casehub.neocortex.cognition.mentalmodel.MentalModelOrchestrator;
import io.casehub.neocortex.cognition.mentalmodel.MentalStateSignal;
import io.casehub.neocortex.mindmap.intelligence.SubThoughtsEnriched;
import org.jspecify.annotations.Nullable;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public class SubThoughtTickParticipant implements CognitionTickParticipant {

    private static final Duration ASYNC_CACHE_TTL = Duration.ofMinutes(5);

    private final RuleBasedSubThoughtExtractor extractor;
    private final @Nullable MentalModelOrchestrator mentalModel;
    private final @Nullable SubjectResolver resolver;

    private final ConcurrentHashMap<String, SubThoughtResult> state = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, AsyncCacheEntry> asyncCache = new ConcurrentHashMap<>();

    public SubThoughtTickParticipant(
            RuleBasedSubThoughtExtractor extractor,
            @Nullable MentalModelOrchestrator mentalModel,
            @Nullable SubjectResolver resolver) {
        this.extractor = extractor;
        this.mentalModel = mentalModel;
        this.resolver = resolver;
    }

    @Override
    public void tick(CognitionTickContext context) {
        var observation = context.observation();
        if (observation == null || observation.isBlank()) {return;}

        var agentId  = context.agentId();
        var tenantId = context.tenantId();
        var key      = agentId + ":" + tenantId;

        List<SubThought> sync = extractor.extract(observation, agentId, tenantId);

        var asyncEntry = asyncCache.get(key);
        List<SubThought> async = (asyncEntry != null && !asyncEntry.isExpired())
                                 ? asyncEntry.subThoughts : List.of();

        List<SubThought> merged = SubThoughts.merge(sync, async);
        state.put(key, new SubThoughtResult(merged, ""));

        pushToMentalModel(merged, agentId, tenantId, context.resolver());
    }

    public @Nullable SubThoughtResult currentSubThoughts(String agentId, String tenantId) {
        return state.get(agentId + ":" + tenantId);
    }

    void handleEnriched(SubThoughtsEnriched event) {
        var enriched = event.subThoughts().stream()
                .map(p -> new SubThought(p.type(), p.text(), p.entity(),
                        p.confidence(), SubThought.Source.ASYNC))
                .toList();
        asyncCache.put(event.agentId() + ":" + event.tenantId(),
                new AsyncCacheEntry(enriched, Instant.now()));
    }

    private void pushToMentalModel(List<SubThought> subThoughts, String agentId,
                                    String tenantId, SubjectResolver contextResolver) {
        if (mentalModel == null) return;
        var resolverToUse = contextResolver != null ? contextResolver : this.resolver;
        if (resolverToUse == null) return;
        Set<String> subjects = resolverToUse.relevantSubjects(agentId, tenantId);
        for (var st : subThoughts) {
            if (st.entity() != null && subjects.contains(st.entity())) {
                mentalModel.record(
                    new MentalStateSignal.SubThoughtCue(st.type(), st.text(), st.entity(), st.confidence()),
                    agentId, st.entity(), tenantId
                );
            }
        }
    }

    private record AsyncCacheEntry(List<SubThought> subThoughts, Instant cachedAt) {
        boolean isExpired() {
            return Duration.between(cachedAt, Instant.now()).compareTo(ASYNC_CACHE_TTL) > 0;
        }
    }
}

package io.casehub.neocortex.mindmap.intelligence;

import io.casehub.neocortex.memory.experience.ExperienceRecorder;
import io.casehub.neocortex.memory.experience.Observation;
import io.casehub.neocortex.mindmap.EdgeInput;
import io.casehub.neocortex.mindmap.MindMapNode;
import io.casehub.neocortex.mindmap.MindMapStore;
import io.casehub.neocortex.mindmap.NodeInput;
import io.casehub.neocortex.mindmap.SubgraphTypes;
import io.casehub.platform.api.identity.PrincipalId;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Event;
import jakarta.enterprise.inject.Instance;
import jakarta.inject.Inject;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

@ApplicationScoped
public class CheckInService {

    private static final Logger LOG = Logger.getLogger(CheckInService.class.getName());

    private final MindMapStore                         store;
    private final ExperienceRecorder                   recorder;
    private final Event<SubThoughtExtractionRequested> extractionEvent;

    @Inject
    public CheckInService(MindMapStore store,
                          Instance<ExperienceRecorder> recorder,
                          Event<SubThoughtExtractionRequested> extractionEvent) {
        this.store           = store;
        this.recorder        = recorder.isResolvable() ? recorder.get() : null;
        this.extractionEvent = extractionEvent;
    }

    CheckInService(MindMapStore store) {
        this.store           = store;
        this.recorder        = null;
        this.extractionEvent = null;
    }

    CheckInService(MindMapStore store, ExperienceRecorder recorder,
                   Event<SubThoughtExtractionRequested> extractionEvent) {
        this.store           = store;
        this.recorder        = recorder;
        this.extractionEvent = extractionEvent;
    }

    public CheckInResult checkIn(CheckInRequest request, String tenantId) {
        return checkIn(request, tenantId, null);
    }

    public CheckInResult checkIn(CheckInRequest request, String tenantId, PrincipalId principalId) {
        String       placeNodeId        = resolveOrCreatePlace(request, tenantId);
        String       activityNodeId     = createActivity(request, placeNodeId, tenantId);
        List<String> participantEdgeIds = linkParticipants(request, activityNodeId, tenantId);

        String memoryId = null;
        if (recorder != null && principalId != null) {
            memoryId = recordExperience(request, tenantId, principalId);
            if (memoryId != null && extractionEvent != null) {
                String description = buildDescription(request);
                extractionEvent.fireAsync(new SubThoughtExtractionRequested(
                        memoryId, tenantId, description, principalId));
            }
        }

        return new CheckInResult(activityNodeId, placeNodeId, participantEdgeIds, memoryId);
    }

    private String recordExperience(CheckInRequest request, String tenantId, PrincipalId principalId) {
        try {
            String description = buildDescription(request);
            var observation = new Observation(
                    principalId.id(), tenantId, null, null,
                    Instant.now(), description, null,
                    Map.of("event-type", "observation", "provenance", "check-in"),
                    request.placeName());
            return recorder.record(observation);
        } catch (Exception e) {
            LOG.log(Level.WARNING, "Failed to record check-in experience", e);
            return null;
        }
    }

    private String buildDescription(CheckInRequest request) {
        var sb = new StringBuilder(request.activityName()).append(" at ").append(request.placeName());
        if (request.notes() != null) {sb.append(": ").append(request.notes());}
        return sb.toString();
    }

    private String resolveOrCreatePlace(CheckInRequest request, String tenantId) {
        String      subgraphId = SubgraphUtils.ensureSubgraph(store, SubgraphTypes.PLACE, tenantId);
        MindMapNode existing   = store.resolveNode(request.placeName(), subgraphId, tenantId);
        if (existing != null) {
            return existing.id();
        }
        Map<String, String> props = new HashMap<>(request.placeProperties());
        return store.addNode(
                NodeInput.of(request.placeName(), subgraphId)
                         .withProperties(props)
                         .withProvenance("check-in"),
                tenantId);
    }

    private String createActivity(CheckInRequest request, String placeNodeId, String tenantId) {
        String              subgraphId = SubgraphUtils.ensureSubgraph(store, SubgraphTypes.ACTIVITY, tenantId);
        Map<String, String> props      = new HashMap<>();
        if (request.date() != null) {props.put("date", request.date().toString());}
        if (request.activityType() != null) {props.put("activityType", request.activityType());}
        if (request.notes() != null) {props.put("notes", request.notes());}

        String activityId = store.addNode(
                NodeInput.of(request.activityName(), subgraphId)
                         .withProperties(props)
                         .withValidFrom(request.date())
                         .withProvenance("check-in"),
                tenantId);

        store.addEdge(EdgeInput.of(activityId, placeNodeId, "at")
                               .withProvenance("check-in"), tenantId);

        return activityId;
    }

    private List<String> linkParticipants(CheckInRequest request, String activityNodeId, String tenantId) {
        List<String> edgeIds          = new ArrayList<>();
        String       personSubgraphId = SubgraphUtils.ensureSubgraph(store, SubgraphTypes.PERSON, tenantId);
        for (var participant : request.participants()) {
            String personId = resolveOrCreatePerson(participant.name(), personSubgraphId, tenantId);
            String edgeId = store.addEdge(
                    EdgeInput.of(personId, activityNodeId, "participated")
                             .withProperties(participant.edgeProperties())
                             .withProvenance("check-in"),
                    tenantId);
            edgeIds.add(edgeId);
        }
        return edgeIds;
    }

    private String resolveOrCreatePerson(String name, String subgraphId, String tenantId) {
        MindMapNode existing = store.resolveNode(name, subgraphId, tenantId);
        if (existing != null) {
            return existing.id();
        }
        return store.addNode(
                NodeInput.of(name, subgraphId).withProvenance("check-in"),
                tenantId);
    }

}

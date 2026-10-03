package io.casehub.neocortex.mindmap.intelligence;

import io.casehub.neocortex.mindmap.MindMapEdge;
import io.casehub.neocortex.mindmap.MindMapNode;
import io.casehub.neocortex.mindmap.MindMapQuery;
import io.casehub.neocortex.mindmap.MindMapStore;
import io.casehub.neocortex.mindmap.SubgraphTypes;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

@ApplicationScoped
public class ActivityQueryService {

    private final MindMapStore store;

    @Inject
    public ActivityQueryService(MindMapStore store) {
        this.store = store;
    }

    public Optional<ActivitySummary> lastSeenWith(String personName, String tenantId) {
        return activitiesWithPerson(personName, 1, tenantId).stream().findFirst();
    }

    public List<ActivitySummary> activitiesWithPerson(String personName, int limit, String tenantId) {
        MindMapNode person = findPerson(personName, tenantId);
        if (person == null) return List.of();

        List<MindMapEdge> participatedEdges = store.neighbors(person.id(), "participated", tenantId);
        List<ActivitySummary> summaries = new ArrayList<>();
        for (var edge : participatedEdges) {
            MindMapNode activity = store.getNode(edge.targetNodeId(), tenantId);
            if (activity != null) {
                summaries.add(buildSummary(activity, tenantId));
            }
        }
        summaries.sort(Comparator.comparing(
            (ActivitySummary s) -> s.date() != null ? s.date() : Instant.EPOCH).reversed());
        return summaries.size() > limit ? summaries.subList(0, limit) : summaries;
    }

    public List<MindMapNode> placesVisited(int limit, String tenantId) {
        List<MindMapNode> activities = store.search(
            MindMapQuery.of(tenantId, 1000).withType(SubgraphTypes.ACTIVITY));
        Set<String> seenPlaceIds = new LinkedHashSet<>();
        List<MindMapNode> places = new ArrayList<>();
        for (var activity : activities) {
            List<MindMapEdge> atEdges = store.neighbors(activity.id(), "at", tenantId);
            for (var edge : atEdges) {
                if (seenPlaceIds.add(edge.targetNodeId())) {
                    MindMapNode place = store.getNode(edge.targetNodeId(), tenantId);
                    if (place != null) places.add(place);
                }
            }
        }
        return places.size() > limit ? places.subList(0, limit) : places;
    }

    public List<ActivitySummary> activitiesAtPlace(String placeName, int limit, String tenantId) {
        MindMapNode place = findPlace(placeName, tenantId);
        if (place == null) return List.of();

        List<MindMapNode> activities = store.search(
            MindMapQuery.of(tenantId, 1000).withType(SubgraphTypes.ACTIVITY));
        List<ActivitySummary> summaries = new ArrayList<>();
        for (var activity : activities) {
            List<MindMapEdge> atEdges = store.neighbors(activity.id(), "at", tenantId);
            boolean atThisPlace = atEdges.stream()
                .anyMatch(e -> e.targetNodeId().equals(place.id()));
            if (atThisPlace) {
                summaries.add(buildSummary(activity, tenantId));
            }
        }
        summaries.sort(Comparator.comparing(
            (ActivitySummary s) -> s.date() != null ? s.date() : Instant.EPOCH).reversed());
        return summaries.size() > limit ? summaries.subList(0, limit) : summaries;
    }

    private ActivitySummary buildSummary(MindMapNode activity, String tenantId) {
        MindMapNode placeNode = null;
        List<MindMapEdge> atEdges = store.neighbors(activity.id(), "at", tenantId);
        if (!atEdges.isEmpty()) {
            placeNode = store.getNode(atEdges.getFirst().targetNodeId(), tenantId);
        }
        List<MindMapEdge> participatedEdges = store.neighbors(activity.id(), "participated", tenantId);
        List<String> names = participatedEdges.stream()
            .map(e -> store.getNode(e.sourceNodeId(), tenantId))
            .filter(n -> n != null)
            .map(MindMapNode::name)
            .toList();
        Instant date = activity.property("date")
            .map(Instant::parse)
            .orElse(activity.validFrom());
        return new ActivitySummary(activity, placeNode, names, date);
    }

    private MindMapNode findPerson(String name, String tenantId) {
        return store.listSubgraphs(tenantId).stream()
            .filter(s -> SubgraphTypes.PERSON.equals(s.type()))
            .map(s -> store.resolveNode(name, s.id(), tenantId))
            .filter(n -> n != null)
            .findFirst().orElse(null);
    }

    private MindMapNode findPlace(String name, String tenantId) {
        return store.listSubgraphs(tenantId).stream()
            .filter(s -> SubgraphTypes.PLACE.equals(s.type()))
            .map(s -> store.resolveNode(name, s.id(), tenantId))
            .filter(n -> n != null)
            .findFirst().orElse(null);
    }
}

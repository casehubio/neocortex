package io.casehub.neocortex.mindmap.intelligence;

import io.casehub.neocortex.mindmap.MindMapEdge;
import io.casehub.neocortex.mindmap.MindMapNode;
import io.casehub.neocortex.mindmap.MindMapQuery;
import io.casehub.neocortex.mindmap.SubgraphTypes;
import io.casehub.neocortex.mindmap.inmem.InMemoryMindMapStore;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class CheckInServiceTest {

    private static final String TENANT = "t1";
    private InMemoryMindMapStore store;
    private CheckInService service;

    @BeforeEach
    void setUp() {
        store = new InMemoryMindMapStore();
        service = new CheckInService(store);
    }

    @Test
    void checkIn_createsActivityNode() {
        var request = CheckInRequest.of("Dinner at Ondine", "Ondine")
            .withDate(Instant.parse("2026-10-01T19:00:00Z"))
            .withActivityType("dining")
            .withNotes("great lobster");

        CheckInResult result = service.checkIn(request, TENANT);

        MindMapNode activity = store.getNode(result.activityNodeId(), TENANT);
        assertThat(activity.name()).isEqualTo("Dinner at Ondine");
        assertThat(activity.subgraphType()).isEqualTo(SubgraphTypes.ACTIVITY);
        assertThat(activity.property("activityType")).hasValue("dining");
        assertThat(activity.property("notes")).hasValue("great lobster");
        assertThat(activity.property("date")).hasValue("2026-10-01T19:00:00Z");
    }

    @Test
    void checkIn_createsPlaceNode() {
        var request = CheckInRequest.of("Dinner at Ondine", "Ondine")
            .withPlaceProperties(Map.of("address", "2 George IV Bridge", "category", "restaurant"));

        CheckInResult result = service.checkIn(request, TENANT);

        MindMapNode place = store.getNode(result.placeNodeId(), TENANT);
        assertThat(place.name()).isEqualTo("Ondine");
        assertThat(place.subgraphType()).isEqualTo(SubgraphTypes.PLACE);
        assertThat(place.property("address")).hasValue("2 George IV Bridge");
        assertThat(place.property("category")).hasValue("restaurant");
    }

    @Test
    void checkIn_linksActivityToPlace() {
        var request = CheckInRequest.of("Dinner at Ondine", "Ondine");

        CheckInResult result = service.checkIn(request, TENANT);

        List<MindMapEdge> edges = store.neighbors(result.activityNodeId(), "at", TENANT);
        assertThat(edges).hasSize(1);
        assertThat(edges.getFirst().targetNodeId()).isEqualTo(result.placeNodeId());
    }

    @Test
    void checkIn_linksParticipants() {
        var request = CheckInRequest.of("Dinner at Ondine", "Ondine")
            .withParticipants(List.of(
                new CheckInRequest.Participant("Sarah", Map.of("recommended", "true")),
                new CheckInRequest.Participant("Tom")));

        CheckInResult result = service.checkIn(request, TENANT);

        assertThat(result.participantEdgeIds()).hasSize(2);
        List<MindMapEdge> participantEdges = store.neighbors(result.activityNodeId(), "participated", TENANT);
        assertThat(participantEdges).hasSize(2);
    }

    @Test
    void checkIn_participantEdgeCarriesProperties() {
        var request = CheckInRequest.of("Dinner at Ondine", "Ondine")
            .withParticipants(List.of(
                new CheckInRequest.Participant("Sarah", Map.of("rating", "5", "recommended", "true"))));

        CheckInResult result = service.checkIn(request, TENANT);

        MindMapEdge edge = store.getEdge(result.participantEdgeIds().getFirst(), TENANT);
        assertThat(edge.edgeType()).isEqualTo("participated");
        assertThat(edge.property("rating")).hasValue("5");
        assertThat(edge.property("recommended")).hasValue("true");
    }

    @Test
    void checkIn_reusesExistingPlace() {
        var first = CheckInRequest.of("Lunch at Ondine", "Ondine");
        CheckInResult r1 = service.checkIn(first, TENANT);

        var second = CheckInRequest.of("Dinner at Ondine", "Ondine");
        CheckInResult r2 = service.checkIn(second, TENANT);

        assertThat(r2.placeNodeId()).isEqualTo(r1.placeNodeId());
    }

    @Test
    void checkIn_reusesExistingPerson() {
        var first = CheckInRequest.of("Lunch", "Ondine")
            .withParticipants(List.of(new CheckInRequest.Participant("Sarah")));
        service.checkIn(first, TENANT);

        var second = CheckInRequest.of("Dinner", "Costa")
            .withParticipants(List.of(new CheckInRequest.Participant("Sarah")));
        service.checkIn(second, TENANT);

        List<MindMapNode> persons = store.search(
            MindMapQuery.of(TENANT, 10).withText("Sarah").withType(SubgraphTypes.PERSON));
        assertThat(persons).hasSize(1);
    }
}

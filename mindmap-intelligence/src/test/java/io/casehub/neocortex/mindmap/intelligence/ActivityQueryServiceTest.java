package io.casehub.neocortex.mindmap.intelligence;

import io.casehub.neocortex.mindmap.MindMapNode;
import io.casehub.neocortex.mindmap.inmem.InMemoryMindMapStore;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class ActivityQueryServiceTest {

    private static final String TENANT = "t1";
    private InMemoryMindMapStore store;
    private CheckInService checkInService;
    private ActivityQueryService queryService;

    @BeforeEach
    void setUp() {
        store = new InMemoryMindMapStore();
        checkInService = new CheckInService(store);
        queryService = new ActivityQueryService(store);
    }

    @Test
    void lastSeenWith_returnsLatestActivity() {
        checkInService.checkIn(
            CheckInRequest.of("Lunch", "Ondine")
                .withDate(Instant.parse("2026-09-01T12:00:00Z"))
                .withParticipants(List.of(new CheckInRequest.Participant("Sarah"))),
            TENANT);
        checkInService.checkIn(
            CheckInRequest.of("Dinner", "Costa")
                .withDate(Instant.parse("2026-10-01T19:00:00Z"))
                .withParticipants(List.of(new CheckInRequest.Participant("Sarah"))),
            TENANT);

        Optional<ActivitySummary> result = queryService.lastSeenWith("Sarah", TENANT);

        assertThat(result).isPresent();
        assertThat(result.get().activityNode().name()).isEqualTo("Dinner");
        assertThat(result.get().date()).isEqualTo(Instant.parse("2026-10-01T19:00:00Z"));
    }

    @Test
    void lastSeenWith_emptyWhenNeverMet() {
        Optional<ActivitySummary> result = queryService.lastSeenWith("Unknown", TENANT);
        assertThat(result).isEmpty();
    }

    @Test
    void activitiesWithPerson_returnsAllShared() {
        checkInService.checkIn(
            CheckInRequest.of("Lunch", "Ondine")
                .withDate(Instant.parse("2026-09-01T12:00:00Z"))
                .withParticipants(List.of(new CheckInRequest.Participant("Sarah"))),
            TENANT);
        checkInService.checkIn(
            CheckInRequest.of("Dinner", "Costa")
                .withDate(Instant.parse("2026-10-01T19:00:00Z"))
                .withParticipants(List.of(new CheckInRequest.Participant("Sarah"))),
            TENANT);
        checkInService.checkIn(
            CheckInRequest.of("Coffee", "Starbucks")
                .withDate(Instant.parse("2026-10-02T10:00:00Z"))
                .withParticipants(List.of(new CheckInRequest.Participant("Tom"))),
            TENANT);

        List<ActivitySummary> result = queryService.activitiesWithPerson("Sarah", 10, TENANT);
        assertThat(result).hasSize(2);
    }

    @Test
    void placesVisited_returnsDistinctPlaces() {
        checkInService.checkIn(CheckInRequest.of("Lunch 1", "Ondine"), TENANT);
        checkInService.checkIn(CheckInRequest.of("Lunch 2", "Ondine"), TENANT);
        checkInService.checkIn(CheckInRequest.of("Coffee", "Costa"), TENANT);

        List<MindMapNode> places = queryService.placesVisited(10, TENANT);
        assertThat(places).hasSize(2);
        assertThat(places).extracting(MindMapNode::name)
            .containsExactlyInAnyOrder("Ondine", "Costa");
    }

    @Test
    void activitiesAtPlace_returnsAll() {
        checkInService.checkIn(
            CheckInRequest.of("Lunch", "Ondine")
                .withDate(Instant.parse("2026-09-01T12:00:00Z")),
            TENANT);
        checkInService.checkIn(
            CheckInRequest.of("Dinner", "Ondine")
                .withDate(Instant.parse("2026-10-01T19:00:00Z")),
            TENANT);
        checkInService.checkIn(
            CheckInRequest.of("Coffee", "Costa")
                .withDate(Instant.parse("2026-10-02T10:00:00Z")),
            TENANT);

        List<ActivitySummary> result = queryService.activitiesAtPlace("Ondine", 10, TENANT);
        assertThat(result).hasSize(2);
        assertThat(result).extracting(s -> s.activityNode().name())
            .containsExactlyInAnyOrder("Lunch", "Dinner");
    }
}

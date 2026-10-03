package io.casehub.neocortex.mindmap.intelligence;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public record CheckInRequest(
        String activityName,
        String placeName,
        List<Participant> participants,
        Instant date,
        String activityType,
        String notes,
        Map<String, String> placeProperties
) {
    public CheckInRequest {
        Objects.requireNonNull(activityName, "activityName");
        Objects.requireNonNull(placeName, "placeName");
        participants = participants == null ? List.of() : List.copyOf(participants);
        placeProperties = placeProperties == null ? Map.of() : Map.copyOf(placeProperties);
    }

    public record Participant(String name, Map<String, String> edgeProperties) {
        public Participant {
            Objects.requireNonNull(name, "name");
            edgeProperties = edgeProperties == null ? Map.of() : Map.copyOf(edgeProperties);
        }

        public Participant(String name) { this(name, Map.of()); }
    }

    public static CheckInRequest of(String activityName, String placeName) {
        return new CheckInRequest(activityName, placeName, List.of(),
                                  Instant.now(), null, null, Map.of());
    }

    public CheckInRequest withParticipants(List<Participant> participants) {
        return new CheckInRequest(activityName, placeName, participants,
                                  date, activityType, notes, placeProperties);
    }

    public CheckInRequest withDate(Instant date) {
        return new CheckInRequest(activityName, placeName, participants,
                                  date, activityType, notes, placeProperties);
    }

    public CheckInRequest withActivityType(String activityType) {
        return new CheckInRequest(activityName, placeName, participants,
                                  date, activityType, notes, placeProperties);
    }

    public CheckInRequest withNotes(String notes) {
        return new CheckInRequest(activityName, placeName, participants,
                                  date, activityType, notes, placeProperties);
    }

    public CheckInRequest withPlaceProperties(Map<String, String> placeProperties) {
        return new CheckInRequest(activityName, placeName, participants,
                                  date, activityType, notes, placeProperties);
    }
}

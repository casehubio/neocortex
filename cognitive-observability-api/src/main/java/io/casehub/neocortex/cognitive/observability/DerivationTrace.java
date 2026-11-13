package io.casehub.neocortex.cognitive.observability;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class DerivationTrace implements DerivationObserver {

    public sealed interface Event permits FacetMapped, FacetModulation, StageCompleted {}

    public record FacetMapped(String agentId, String facet, int rawScore, double normalized) implements Event {}

    public record FacetModulation(String agentId, String method, String facet,
                                   String field, double before, double after) implements Event {
        public double delta() { return after - before; }
    }

    public record StageCompleted(String agentId, String method) implements Event {}

    private final List<Event> events = new ArrayList<>();

    @Override
    public void onFacetMapped(String agentId, String facet, int rawScore, double normalized) {
        events.add(new FacetMapped(agentId, facet, rawScore, normalized));
    }

    @Override
    public void onFacetModulation(String agentId, String method, String facet,
                                   String field, double before, double after) {
        events.add(new FacetModulation(agentId, method, facet, field, before, after));
    }

    @Override
    public void onStageComplete(String agentId, String method) {
        events.add(new StageCompleted(agentId, method));
    }

    public List<Event> events() { return Collections.unmodifiableList(events); }

    public List<FacetModulation> modulations() {
        return events.stream()
                .filter(e -> e instanceof FacetModulation)
                .map(e -> (FacetModulation) e)
                .toList();
    }

    public List<FacetModulation> modulationsFor(String facet) {
        return modulations().stream()
                .filter(m -> facet.equals(m.facet()))
                .toList();
    }

    public List<FacetModulation> modulationsIn(String method) {
        return modulations().stream()
                .filter(m -> method.equals(m.method()))
                .toList();
    }

    public List<FacetMapped> mappings() {
        return events.stream()
                .filter(e -> e instanceof FacetMapped)
                .map(e -> (FacetMapped) e)
                .toList();
    }

    public boolean isEmpty() { return events.isEmpty(); }

    public int size() { return events.size(); }
}

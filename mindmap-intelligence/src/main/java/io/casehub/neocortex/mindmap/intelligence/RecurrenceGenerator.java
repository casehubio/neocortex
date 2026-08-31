package io.casehub.neocortex.mindmap.intelligence;

import io.casehub.neocortex.mindmap.MindMapNode;
import io.casehub.neocortex.mindmap.NodeInput;
import io.casehub.neocortex.mindmap.RecurrenceRule;

import java.time.Instant;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class RecurrenceGenerator {

    private RecurrenceGenerator() {}

    public static List<NodeInput> generateInstances(MindMapNode template,
                                                     RecurrenceRule rule,
                                                     Instant horizon) {
        if (template.validFrom() == null) return List.of();

        List<NodeInput> instances = new ArrayList<>();
        ZonedDateTime cursor = template.validFrom().atZone(ZoneOffset.UTC);
        int generated = 0;

        while (true) {
            cursor = advance(cursor, rule);
            Instant candidateInstant = cursor.toInstant();

            if (candidateInstant.isAfter(horizon)) break;
            if (rule.until() != null && candidateInstant.isAfter(rule.until())) break;
            if (rule.count() != null && generated >= rule.count()) break;

            Map<String, String> props = new HashMap<>(template.properties());
            props.remove("rrule");
            props.put("template-node-id", template.id());
            props.put("recurrence-index", String.valueOf(generated));
            props.putIfAbsent("status", "planned");

            instances.add(new NodeInput(
                template.name(), template.subgraphId(),
                template.confidence(), template.provenance(),
                template.traits(), template.refs(),
                candidateInstant, template.validUntil(),
                template.pleasure(), template.arousal(), template.dominance(),
                props));

            generated++;
        }

        return List.copyOf(instances);
    }

    private static ZonedDateTime advance(ZonedDateTime from, RecurrenceRule rule) {
        return switch (rule.freq()) {
            case DAILY -> from.plusDays(rule.interval());
            case WEEKLY -> from.plusWeeks(rule.interval());
            case MONTHLY -> from.plusMonths(rule.interval());
            case YEARLY -> from.plusYears(rule.interval());
        };
    }
}

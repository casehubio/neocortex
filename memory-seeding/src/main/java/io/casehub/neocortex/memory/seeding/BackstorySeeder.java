package io.casehub.neocortex.memory.seeding;

import io.casehub.neocortex.memory.experience.ExperienceEvent;
import io.casehub.neocortex.memory.experience.ExperienceRecorder;
import io.casehub.neocortex.memory.experience.FormativeExperience;

import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class BackstorySeeder {

    private final CatalogueLoader catalogueLoader;
    private final ExperienceRecorder recorder;
    private final Path catalogueDir;
    private final Set<String> seededAgents = Collections.synchronizedSet(new HashSet<>());

    public BackstorySeeder(CatalogueLoader catalogueLoader,
                           ExperienceRecorder recorder,
                           Path catalogueDir) {
        this.catalogueLoader = catalogueLoader;
        this.recorder = recorder;
        this.catalogueDir = catalogueDir;
    }

    public void seed(BackstoryProfile profile) {
        if (!seededAgents.add(profile.agentId())) {
            throw new IllegalStateException(
                "Agent " + profile.agentId() + " already has formative memories");
        }

        var entries = catalogueLoader.loadAll(catalogueDir);
        var events = new ArrayList<FormativeExperience>();

        for (var selection : profile.selections()) {
            var entry = catalogueLoader.findEntry(entries, selection.entryId())
                .orElseThrow(() -> new IllegalArgumentException(
                    "Catalogue entry not found: " + selection.entryId()));
            events.addAll(generateEvents(profile, entry, selection));
        }

        events.sort(Comparator.comparing(FormativeExperience::timestamp));

        recorder.recordAll(new ArrayList<>(events));
    }

    private List<FormativeExperience> generateEvents(
            BackstoryProfile profile, CatalogueEntry entry,
            BackstoryProfile.CatalogueSelection selection) {
        var events = new ArrayList<FormativeExperience>();
        String period = entry.developmentalPeriod();
        double salience = SalienceDefaults.forPeriod(period);

        for (var trigger : entry.triggers()) {
            int reps = repetitionCount(trigger.repetition(), selection.repetitionOverride());
            double intensity = triggerIntensity(trigger, selection.intensityOverride());
            var timestamps = FormativeTimestampGenerator.generate(reps, period, Instant.EPOCH);
            var pad = PadDeriver.derive(trigger.node(), intensity);

            for (int i = 0; i < reps; i++) {
                String desc = generateDescription(entry, trigger, i);
                events.add(new FormativeExperience(
                    profile.agentId(), profile.tenantId(), null, null,
                    timestamps.get(i), desc, 0.8, Map.of(),
                    entry.id(), List.of(trigger.node()), salience,
                    trigger.schedule(), period,
                    pad.pleasure(), pad.arousal(), pad.dominance()));
            }
        }
        return events;
    }

    private int repetitionCount(String repetition, Integer override) {
        if (override != null) return override;
        return switch (repetition != null ? repetition : "moderate") {
            case "high"     -> 10;
            case "moderate" -> 5;
            case "low"      -> 2;
            default         -> 5;
        };
    }

    private double triggerIntensity(TriggerSpec trigger, Double override) {
        if (override != null) return override;
        var range = trigger.intensity();
        return (range.get(0) + range.get(1)) / 2.0;
    }

    private String generateDescription(CatalogueEntry entry, TriggerSpec trigger, int index) {
        if (entry.narratives() != null && !entry.narratives().isEmpty()) {
            return entry.narratives().get(index % entry.narratives().size());
        }
        return "Experienced " + entry.clinicalName().toLowerCase() + " [" + trigger.node() + "]";
    }
}

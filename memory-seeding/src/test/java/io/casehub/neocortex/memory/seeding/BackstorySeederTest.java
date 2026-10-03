package io.casehub.neocortex.memory.seeding;

import io.casehub.neocortex.memory.experience.ExperienceEvent;
import io.casehub.neocortex.memory.experience.ExperienceRecorder;
import io.casehub.neocortex.memory.experience.ExperienceStoreResult;
import io.casehub.neocortex.memory.experience.FormativeExperience;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class BackstorySeederTest {

    @Test
    void seedGeneratesFormativeExperiences(@TempDir Path tempDir) throws Exception {
        writeCatalogueFixtures(tempDir);

        var events = new ArrayList<ExperienceEvent>();
        var recorder = capturingRecorder(events);

        var seeder = new BackstorySeeder(new CatalogueLoader(), recorder, tempDir);

        var profile = new BackstoryProfile("agent1", "tenant1",
            List.of(new BackstoryProfile.CatalogueSelection(
                "attachment-secure", null, null)),
            Map.of());

        seeder.seed(profile);

        assertThat(events).isNotEmpty();
        assertThat(events).allSatisfy(e -> {
            assertThat(e).isInstanceOf(FormativeExperience.class);
            var fe = (FormativeExperience) e;
            assertThat(fe.agentId()).isEqualTo("agent1");
            assertThat(fe.tenantId()).isEqualTo("tenant1");
            assertThat(fe.catalogueEntryId()).isEqualTo("attachment-secure");
            assertThat(fe.situationTypes()).contains("secure_attachment");
            assertThat(fe.salienceMultiplier()).isEqualTo(3.0);
            assertThat(fe.developmentalPeriod()).isEqualTo("infancy");
        });
        assertThat(events).hasSize(10);
    }

    @Test
    void repetitionOverrideAffectsCount(@TempDir Path tempDir) throws Exception {
        writeCatalogueFixtures(tempDir);

        var events = new ArrayList<ExperienceEvent>();
        var recorder = capturingRecorder(events);

        var seeder = new BackstorySeeder(new CatalogueLoader(), recorder, tempDir);

        var profile = new BackstoryProfile("agent1", "tenant1",
            List.of(new BackstoryProfile.CatalogueSelection(
                "attachment-secure", null, 3)),
            Map.of());

        seeder.seed(profile);

        assertThat(events).hasSize(3);
    }

    @Test
    void multipleTriggersGenerateEvents(@TempDir Path tempDir) throws Exception {
        writeCatalogueFixtures(tempDir);

        var events = new ArrayList<ExperienceEvent>();
        var recorder = capturingRecorder(events);

        var seeder = new BackstorySeeder(new CatalogueLoader(), recorder, tempDir);

        var profile = new BackstoryProfile("agent1", "tenant1",
            List.of(new BackstoryProfile.CatalogueSelection(
                "attachment-anxious", null, null)),
            Map.of());

        seeder.seed(profile);

        assertThat(events).hasSize(15);
    }

    @Test
    void eventsOrderedByTimestamp(@TempDir Path tempDir) throws Exception {
        writeCatalogueFixtures(tempDir);

        var events = new ArrayList<ExperienceEvent>();
        var recorder = capturingRecorder(events);

        var seeder = new BackstorySeeder(new CatalogueLoader(), recorder, tempDir);

        var profile = new BackstoryProfile("agent1", "tenant1",
            List.of(new BackstoryProfile.CatalogueSelection(
                "attachment-secure", null, null)),
            Map.of());

        seeder.seed(profile);

        for (int i = 1; i < events.size(); i++) {
            assertThat(events.get(i).timestamp())
                .isAfterOrEqualTo(events.get(i - 1).timestamp());
        }
    }

    @Test
    void narrativesCycleRoundRobin(@TempDir Path tempDir) throws Exception {
        writeCatalogueFixtures(tempDir);

        var events = new ArrayList<ExperienceEvent>();
        var recorder = capturingRecorder(events);

        var seeder = new BackstorySeeder(new CatalogueLoader(), recorder, tempDir);

        var profile = new BackstoryProfile("agent1", "tenant1",
            List.of(new BackstoryProfile.CatalogueSelection(
                "attachment-secure", null, 4)),
            Map.of());

        seeder.seed(profile);

        var descriptions = events.stream()
            .map(ExperienceEvent::description).toList();
        assertThat(descriptions.get(0)).contains("caregiver responded warmly");
        assertThat(descriptions.get(1)).contains("Reaching out for comfort");
        assertThat(descriptions.get(2)).contains("caregiver responded warmly");
        assertThat(descriptions.get(3)).contains("Reaching out for comfort");
    }

    @Test
    void padValuesPresent(@TempDir Path tempDir) throws Exception {
        writeCatalogueFixtures(tempDir);

        var events = new ArrayList<ExperienceEvent>();
        var recorder = capturingRecorder(events);

        var seeder = new BackstorySeeder(new CatalogueLoader(), recorder, tempDir);

        var profile = new BackstoryProfile("agent1", "tenant1",
            List.of(new BackstoryProfile.CatalogueSelection(
                "attachment-secure", null, 1)),
            Map.of());

        seeder.seed(profile);

        var fe = (FormativeExperience) events.getFirst();
        assertThat(fe.pleasure()).isNotNull();
        assertThat(fe.arousal()).isNotNull();
        assertThat(fe.dominance()).isNotNull();
    }

    @Test
    void idempotencyRejectsDoubleSeeding(@TempDir Path tempDir) throws Exception {
        writeCatalogueFixtures(tempDir);

        var events = new ArrayList<ExperienceEvent>();
        var recorder = capturingRecorder(events);

        var seeder = new BackstorySeeder(new CatalogueLoader(), recorder, tempDir);

        var profile = new BackstoryProfile("agent1", "tenant1",
            List.of(new BackstoryProfile.CatalogueSelection(
                "attachment-secure", null, null)),
            Map.of());

        seeder.seed(profile);
        assertThatThrownBy(() -> seeder.seed(profile))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("already has formative memories");
    }

    @Test
    void unknownEntryThrows(@TempDir Path tempDir) throws Exception {
        writeCatalogueFixtures(tempDir);

        var events = new ArrayList<ExperienceEvent>();
        var recorder = capturingRecorder(events);

        var seeder = new BackstorySeeder(new CatalogueLoader(), recorder, tempDir);

        var profile = new BackstoryProfile("agent1", "tenant1",
            List.of(new BackstoryProfile.CatalogueSelection(
                "nonexistent-entry", null, null)),
            Map.of());

        assertThatThrownBy(() -> seeder.seed(profile))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("nonexistent-entry");
    }

    private ExperienceRecorder capturingRecorder(List<ExperienceEvent> events) {
        return new ExperienceRecorder() {
            @Override
            public String record(ExperienceEvent e) {
                events.add(e);
                return "id";
            }

            @Override
            public ExperienceStoreResult recordAll(List<ExperienceEvent> list) {
                events.addAll(list);
                return new ExperienceStoreResult(
                    list.stream().map(e -> "mem-" + list.indexOf(e)).toList(),
                    List.of());
            }
        };
    }

    private void writeCatalogueFixtures(Path dir) throws Exception {
        Files.writeString(dir.resolve("index.yaml"),
            "version: '1.0.0'\ncaps_topology_version: 1\nspec_issue: 401\nparent_epic: 406\n");
        Files.writeString(dir.resolve("test-attachment.yaml"), """
            entries:
              - id: attachment-secure
                model: attachment
                clinical_name: Secure attachment
                description: Consistent caregiver responsiveness
                triggers:
                  - node: secure_attachment
                    intensity: [0.6, 0.9]
                    repetition: high
                    schedule: continuous
                narratives:
                  - "Your caregiver responded warmly and reliably."
                  - "Reaching out for comfort produced a predictable response."
                expected_outcomes:
                  - node: self_worth
                    direction: positive
                    strength: [0.4, 0.7]
                developmental_period: infancy
                sources:
                  - ref: van IJzendoorn 1995
                    data: r=.24-.32
                    provenance: empirical
              - id: attachment-anxious
                model: attachment
                clinical_name: Anxious-preoccupied attachment
                description: Variable-ratio reinforcement
                triggers:
                  - node: inconsistent_care
                    intensity: [0.6, 0.9]
                    repetition: high
                    schedule: variable_ratio
                  - node: rejection
                    intensity: [0.3, 0.5]
                    repetition: moderate
                expected_outcomes:
                  - node: proximity_seek
                    direction: positive
                    strength: [0.5, 0.8]
                developmental_period: infancy
                sources:
                  - ref: Cassidy & Berlin 1994
                    data: ambivalent classification
                    provenance: empirical
            """);
    }
}

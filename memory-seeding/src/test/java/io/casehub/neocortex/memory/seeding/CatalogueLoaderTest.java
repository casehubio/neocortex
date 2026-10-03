package io.casehub.neocortex.memory.seeding;

import org.junit.jupiter.api.Test;

import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class CatalogueLoaderTest {

    private final CatalogueLoader loader = new CatalogueLoader();
    private final Path testDir = Path.of("src/test/resources/catalogue");

    @Test
    void loadIndex() {
        var index = loader.loadIndex(testDir);
        assertThat(index.version()).isEqualTo("1.0.0");
        assertThat(index.capsTopologyVersion()).isEqualTo(1);
    }

    @Test
    void loadAllEntries() {
        var entries = loader.loadAll(testDir);
        assertThat(entries).hasSize(2);
        assertThat(entries).extracting(CatalogueEntry::id)
            .containsExactlyInAnyOrder("attachment-secure", "attachment-anxious");
    }

    @Test
    void findEntryById() {
        var entries = loader.loadAll(testDir);
        var found = loader.findEntry(entries, "attachment-anxious");
        assertThat(found).isPresent();
        assertThat(found.get().clinicalName()).isEqualTo("Anxious-preoccupied attachment");
        assertThat(found.get().triggers()).hasSize(2);
        assertThat(found.get().triggers().getFirst().schedule()).isEqualTo("variable_ratio");
    }

    @Test
    void findEntryMissing() {
        var entries = loader.loadAll(testDir);
        assertThat(loader.findEntry(entries, "nonexistent")).isEmpty();
    }

    @Test
    void narrativesLoadedCorrectly() {
        var entries = loader.loadAll(testDir);
        var secure = loader.findEntry(entries, "attachment-secure").orElseThrow();
        assertThat(secure.narratives()).hasSize(2);
        assertThat(secure.narratives().getFirst()).contains("caregiver responded warmly");
    }

    @Test
    void developmentalPeriodLoaded() {
        var entries = loader.loadAll(testDir);
        var secure = loader.findEntry(entries, "attachment-secure").orElseThrow();
        assertThat(secure.developmentalPeriod()).isEqualTo("infancy");
    }
}

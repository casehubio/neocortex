package io.casehub.neocortex.memory.seeding.biography;

import io.casehub.neocortex.memory.seeding.BackstoryProfile;
import io.casehub.neocortex.memory.seeding.BackstorySeeder;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Instance;
import jakarta.inject.Inject;

import java.util.ArrayList;
import java.util.Map;
import java.util.Set;
import java.util.logging.Logger;

@ApplicationScoped
public class FormativeExperienceHandler implements BiographyHandler {

    private static final Logger LOG = Logger.getLogger(FormativeExperienceHandler.class.getName());

    private final BackstorySeeder seeder;

    @Inject
    public FormativeExperienceHandler(Instance<BackstorySeeder> seeder) {
        this.seeder = seeder.isResolvable() ? seeder.get() : null;
    }

    FormativeExperienceHandler(BackstorySeeder seeder) {
        this.seeder = seeder;
    }

    @Override public Set<String> handledTypes() { return Set.of(BiographyTemplateTypes.FORMATIVE_EXPERIENCE); }

    @Override
    public void handle(BiographyProfile profile, String agentId, String tenantId) {
        if (seeder == null) {
            LOG.warning("BackstorySeeder not available — skipping formative experiences");
            return;
        }

        var selections = new ArrayList<BackstoryProfile.CatalogueSelection>();
        for (var entry : profile.formativeExperiences()) {
            selections.add(new BackstoryProfile.CatalogueSelection(
                entry.catalogueEntryId(),
                entry.intensityOverride(),
                entry.repetitionOverride()));
        }

        if (!selections.isEmpty()) {
            seeder.seed(new BackstoryProfile(agentId, tenantId, selections, Map.of()));
        }
    }
}

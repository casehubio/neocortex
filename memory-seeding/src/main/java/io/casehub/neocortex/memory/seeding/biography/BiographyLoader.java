package io.casehub.neocortex.memory.seeding.biography;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.casehub.yaml.jackson.YamlMappers;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.stream.Stream;

public class BiographyLoader {

    private final ObjectMapper mapper = YamlMappers.create();

    public BiographyProfile loadAll(Path biographyDir, String agentId, String tenantId) {
        var culturalContexts = new ArrayList<CulturalContextEntry>();
        var formativeExperiences = new ArrayList<FormativeExperienceEntry>();
        var lifeEvents = new ArrayList<LifeEventEntry>();
        var places = new ArrayList<PlaceEntry>();
        var activities = new ArrayList<ActivityEntry>();
        var projects = new ArrayList<ProjectEntry>();
        var relationships = new ArrayList<RelationshipEntry>();
        var goals = new ArrayList<GoalEntry>();
        var beliefs = new ArrayList<BeliefEntry>();
        var currentStates = new ArrayList<CurrentStateEntry>();

        try (Stream<Path> files = Files.list(biographyDir)) {
            files.filter(p -> p.toString().endsWith(".yaml"))
                 .sorted()
                 .forEach(p -> loadFile(p, culturalContexts, formativeExperiences,
                     lifeEvents, places, activities, projects,
                     relationships, goals, beliefs, currentStates));
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to list biography directory " + biographyDir, e);
        }

        var profile = new BiographyProfile(agentId, tenantId,
            culturalContexts, formativeExperiences, lifeEvents, places,
            activities, projects, relationships, goals, beliefs, currentStates);

        validate(profile);
        return profile;
    }

    private void loadFile(Path file,
                          List<CulturalContextEntry> cc, List<FormativeExperienceEntry> fe,
                          List<LifeEventEntry> le, List<PlaceEntry> pl,
                          List<ActivityEntry> ac, List<ProjectEntry> pr,
                          List<RelationshipEntry> re, List<GoalEntry> go,
                          List<BeliefEntry> be, List<CurrentStateEntry> cs) {
        try {
            var header = mapper.readValue(file.toFile(), TypeHeader.class);
            if (header.type() == null) {
                throw new IllegalStateException("Missing 'type' field in " + file);
            }
            switch (header.type()) {
                case BiographyTemplateTypes.CULTURAL_CONTEXT ->
                    cc.addAll(mapper.readValue(file.toFile(), CulturalContextFile.class).entries());
                case BiographyTemplateTypes.FORMATIVE_EXPERIENCE ->
                    fe.addAll(mapper.readValue(file.toFile(), FormativeExperienceFile.class).entries());
                case BiographyTemplateTypes.LIFE_EVENT ->
                    le.addAll(mapper.readValue(file.toFile(), LifeEventFile.class).entries());
                case BiographyTemplateTypes.PLACE ->
                    pl.addAll(mapper.readValue(file.toFile(), PlaceFile.class).entries());
                case BiographyTemplateTypes.ACTIVITY ->
                    ac.addAll(mapper.readValue(file.toFile(), ActivityFile.class).entries());
                case BiographyTemplateTypes.PROJECT ->
                    pr.addAll(mapper.readValue(file.toFile(), ProjectFile.class).entries());
                case BiographyTemplateTypes.RELATIONSHIP ->
                    re.addAll(mapper.readValue(file.toFile(), RelationshipFile.class).entries());
                case BiographyTemplateTypes.GOAL ->
                    go.addAll(mapper.readValue(file.toFile(), GoalFile.class).entries());
                case BiographyTemplateTypes.BELIEF ->
                    be.addAll(mapper.readValue(file.toFile(), BeliefFile.class).entries());
                case BiographyTemplateTypes.CURRENT_STATE ->
                    cs.addAll(mapper.readValue(file.toFile(), CurrentStateFile.class).entries());
                default -> throw new IllegalStateException("Unknown type: " + header.type() + " in " + file);
            }
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to load " + file, e);
        }
    }

    void validate(BiographyProfile profile) {
        var ids = new HashSet<String>();
        validateIds(ids, profile.culturalContexts().stream().map(CulturalContextEntry::id).toList());
        validateIds(ids, profile.formativeExperiences().stream().map(FormativeExperienceEntry::id).toList());
        validateIds(ids, profile.lifeEvents().stream().map(LifeEventEntry::id).toList());
        validateIds(ids, profile.places().stream().map(PlaceEntry::id).toList());
        validateIds(ids, profile.activities().stream().map(ActivityEntry::id).toList());
        validateIds(ids, profile.projects().stream().map(ProjectEntry::id).toList());
        validateIds(ids, profile.relationships().stream().map(RelationshipEntry::id).toList());
        validateIds(ids, profile.goals().stream().map(GoalEntry::id).toList());
        validateIds(ids, profile.beliefs().stream().map(BeliefEntry::id).toList());
        validateIds(ids, profile.currentStates().stream().map(CurrentStateEntry::id).toList());
    }

    private void validateIds(HashSet<String> seen, List<String> newIds) {
        for (String id : newIds) {
            if (!seen.add(id)) {
                throw new IllegalStateException("Duplicate biography entry ID: " + id);
            }
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record TypeHeader(String type) {}
    @JsonIgnoreProperties(ignoreUnknown = true) record CulturalContextFile(List<CulturalContextEntry> entries) {}
    @JsonIgnoreProperties(ignoreUnknown = true) record FormativeExperienceFile(List<FormativeExperienceEntry> entries) {}
    @JsonIgnoreProperties(ignoreUnknown = true) record LifeEventFile(List<LifeEventEntry> entries) {}
    @JsonIgnoreProperties(ignoreUnknown = true) record PlaceFile(List<PlaceEntry> entries) {}
    @JsonIgnoreProperties(ignoreUnknown = true) record ActivityFile(List<ActivityEntry> entries) {}
    @JsonIgnoreProperties(ignoreUnknown = true) record ProjectFile(List<ProjectEntry> entries) {}
    @JsonIgnoreProperties(ignoreUnknown = true) record RelationshipFile(List<RelationshipEntry> entries) {}
    @JsonIgnoreProperties(ignoreUnknown = true) record GoalFile(List<GoalEntry> entries) {}
    @JsonIgnoreProperties(ignoreUnknown = true) record BeliefFile(List<BeliefEntry> entries) {}
    @JsonIgnoreProperties(ignoreUnknown = true) record CurrentStateFile(List<CurrentStateEntry> entries) {}
}

package io.casehub.neocortex.memory.seeding.biography;

import java.util.List;

public record BiographyProfile(
    String agentId,
    String tenantId,
    List<CulturalContextEntry> culturalContexts,
    List<FormativeExperienceEntry> formativeExperiences,
    List<LifeEventEntry> lifeEvents,
    List<PlaceEntry> places,
    List<ActivityEntry> activities,
    List<ProjectEntry> projects,
    List<OrganisationEntry> organisations,
    List<RelationshipEntry> relationships,
    List<GoalEntry> goals,
    List<BeliefEntry> beliefs,
    List<EntityEnrichmentEntry> entityEnrichments,
    List<DispositionProfileEntry> dispositionProfiles,
    List<PersonalityProfileEntry> personalityProfiles,
    List<CurrentStateEntry> currentStates
) {
    public BiographyProfile {
        culturalContexts = culturalContexts != null ? List.copyOf(culturalContexts) : List.of();
        formativeExperiences = formativeExperiences != null ? List.copyOf(formativeExperiences) : List.of();
        lifeEvents = lifeEvents != null ? List.copyOf(lifeEvents) : List.of();
        places = places != null ? List.copyOf(places) : List.of();
        activities = activities != null ? List.copyOf(activities) : List.of();
        projects = projects != null ? List.copyOf(projects) : List.of();
        organisations = organisations != null ? List.copyOf(organisations) : List.of();
        relationships = relationships != null ? List.copyOf(relationships) : List.of();
        goals = goals != null ? List.copyOf(goals) : List.of();
        beliefs = beliefs != null ? List.copyOf(beliefs) : List.of();
        entityEnrichments = entityEnrichments != null ? List.copyOf(entityEnrichments) : List.of();
        dispositionProfiles = dispositionProfiles != null ? List.copyOf(dispositionProfiles) : List.of();
        personalityProfiles = personalityProfiles != null ? List.copyOf(personalityProfiles) : List.of();
        currentStates = currentStates != null ? List.copyOf(currentStates) : List.of();
    }
}

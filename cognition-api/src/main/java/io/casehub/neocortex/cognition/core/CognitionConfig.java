package io.casehub.neocortex.cognition.core;

public record CognitionConfig(
        boolean moodEnabled,
        boolean drivesEnabled,
        boolean mentalModelEnabled,
        boolean userModelEnabled,
        boolean strategyEnabled,
        boolean narrativeEnabled,
        boolean goalsEnabled,
        boolean memoryHygieneEnabled,
        boolean innerLifeEnabled,
        boolean directivePrompts,
        boolean characterDrivesEnabled,
        boolean needsPyramidEnabled,
        boolean attentionEnabled,
        boolean temporalFocusEnabled,
        boolean reflectionEnabled,
        boolean consolidationEnabled,
        boolean entityKnowledgeEnabled,
        boolean perspectiveComparisonEnabled,
        boolean domainActivationEnabled,
        boolean appraisalEnabled,
        boolean salienceEnabled,
        boolean behavioralEnabled,
        boolean subThoughtsEnabled
) {
    public static CognitionConfig all()  {return new CognitionConfig(true, true, true, true, true, true, true, true, true, false, true, true, true, true, true, true, true, false, false, false, false, false, true);}

    public static CognitionConfig withAllSubsystems() {return new CognitionConfig(true, true, true, true, true, true, true, true, true, true, true, true, true, true, true, true, true, true, true, true, true, true, true);}

    public static CognitionConfig none() {return new CognitionConfig(false, false, false, false, false, false, false, false, false, false, false, false, false, false, false, false, false, false, false, false, false, false, false);}

    public CognitionConfig withDirectives() {
        return new CognitionConfig(moodEnabled, drivesEnabled, mentalModelEnabled,
                                   userModelEnabled, strategyEnabled, narrativeEnabled, goalsEnabled,
                                   memoryHygieneEnabled, innerLifeEnabled, true, characterDrivesEnabled,
                                   needsPyramidEnabled, attentionEnabled, temporalFocusEnabled, reflectionEnabled, consolidationEnabled, entityKnowledgeEnabled, perspectiveComparisonEnabled, domainActivationEnabled, appraisalEnabled, salienceEnabled, behavioralEnabled, subThoughtsEnabled);
    }

    public CognitionConfig with(String subsystem, boolean enabled) {
        return switch (subsystem) {
            case "mood" -> new CognitionConfig(enabled, drivesEnabled, mentalModelEnabled, userModelEnabled, strategyEnabled, narrativeEnabled, goalsEnabled, memoryHygieneEnabled, innerLifeEnabled, directivePrompts, characterDrivesEnabled, needsPyramidEnabled, attentionEnabled, temporalFocusEnabled, reflectionEnabled, consolidationEnabled, entityKnowledgeEnabled, perspectiveComparisonEnabled, domainActivationEnabled, appraisalEnabled, salienceEnabled, behavioralEnabled, subThoughtsEnabled);
            case "drives" -> new CognitionConfig(moodEnabled, enabled, mentalModelEnabled, userModelEnabled, strategyEnabled, narrativeEnabled, goalsEnabled, memoryHygieneEnabled, innerLifeEnabled, directivePrompts, characterDrivesEnabled, needsPyramidEnabled, attentionEnabled, temporalFocusEnabled, reflectionEnabled, consolidationEnabled, entityKnowledgeEnabled, perspectiveComparisonEnabled, domainActivationEnabled, appraisalEnabled, salienceEnabled, behavioralEnabled, subThoughtsEnabled);
            case "mentalModel" -> new CognitionConfig(moodEnabled, drivesEnabled, enabled, userModelEnabled, strategyEnabled, narrativeEnabled, goalsEnabled, memoryHygieneEnabled, innerLifeEnabled, directivePrompts, characterDrivesEnabled, needsPyramidEnabled, attentionEnabled, temporalFocusEnabled, reflectionEnabled, consolidationEnabled, entityKnowledgeEnabled, perspectiveComparisonEnabled, domainActivationEnabled, appraisalEnabled, salienceEnabled, behavioralEnabled, subThoughtsEnabled);
            case "userModel" -> new CognitionConfig(moodEnabled, drivesEnabled, mentalModelEnabled, enabled, strategyEnabled, narrativeEnabled, goalsEnabled, memoryHygieneEnabled, innerLifeEnabled, directivePrompts, characterDrivesEnabled, needsPyramidEnabled, attentionEnabled, temporalFocusEnabled, reflectionEnabled, consolidationEnabled, entityKnowledgeEnabled, perspectiveComparisonEnabled, domainActivationEnabled, appraisalEnabled, salienceEnabled, behavioralEnabled, subThoughtsEnabled);
            case "strategy" -> new CognitionConfig(moodEnabled, drivesEnabled, mentalModelEnabled, userModelEnabled, enabled, narrativeEnabled, goalsEnabled, memoryHygieneEnabled, innerLifeEnabled, directivePrompts, characterDrivesEnabled, needsPyramidEnabled, attentionEnabled, temporalFocusEnabled, reflectionEnabled, consolidationEnabled, entityKnowledgeEnabled, perspectiveComparisonEnabled, domainActivationEnabled, appraisalEnabled, salienceEnabled, behavioralEnabled, subThoughtsEnabled);
            case "narrative" -> new CognitionConfig(moodEnabled, drivesEnabled, mentalModelEnabled, userModelEnabled, strategyEnabled, enabled, goalsEnabled, memoryHygieneEnabled, innerLifeEnabled, directivePrompts, characterDrivesEnabled, needsPyramidEnabled, attentionEnabled, temporalFocusEnabled, reflectionEnabled, consolidationEnabled, entityKnowledgeEnabled, perspectiveComparisonEnabled, domainActivationEnabled, appraisalEnabled, salienceEnabled, behavioralEnabled, subThoughtsEnabled);
            case "goals" -> new CognitionConfig(moodEnabled, drivesEnabled, mentalModelEnabled, userModelEnabled, strategyEnabled, narrativeEnabled, enabled, memoryHygieneEnabled, innerLifeEnabled, directivePrompts, characterDrivesEnabled, needsPyramidEnabled, attentionEnabled, temporalFocusEnabled, reflectionEnabled, consolidationEnabled, entityKnowledgeEnabled, perspectiveComparisonEnabled, domainActivationEnabled, appraisalEnabled, salienceEnabled, behavioralEnabled, subThoughtsEnabled);
            case "memoryHygiene" -> new CognitionConfig(moodEnabled, drivesEnabled, mentalModelEnabled, userModelEnabled, strategyEnabled, narrativeEnabled, goalsEnabled, enabled, innerLifeEnabled, directivePrompts, characterDrivesEnabled, needsPyramidEnabled, attentionEnabled, temporalFocusEnabled, reflectionEnabled, consolidationEnabled, entityKnowledgeEnabled, perspectiveComparisonEnabled, domainActivationEnabled, appraisalEnabled, salienceEnabled, behavioralEnabled, subThoughtsEnabled);
            case "innerLife" -> new CognitionConfig(moodEnabled, drivesEnabled, mentalModelEnabled, userModelEnabled, strategyEnabled, narrativeEnabled, goalsEnabled, memoryHygieneEnabled, enabled, directivePrompts, characterDrivesEnabled, needsPyramidEnabled, attentionEnabled, temporalFocusEnabled, reflectionEnabled, consolidationEnabled, entityKnowledgeEnabled, perspectiveComparisonEnabled, domainActivationEnabled, appraisalEnabled, salienceEnabled, behavioralEnabled, subThoughtsEnabled);
            case "directivePrompts" -> new CognitionConfig(moodEnabled, drivesEnabled, mentalModelEnabled, userModelEnabled, strategyEnabled, narrativeEnabled, goalsEnabled, memoryHygieneEnabled, innerLifeEnabled, enabled, characterDrivesEnabled, needsPyramidEnabled, attentionEnabled, temporalFocusEnabled, reflectionEnabled, consolidationEnabled, entityKnowledgeEnabled, perspectiveComparisonEnabled, domainActivationEnabled, appraisalEnabled, salienceEnabled, behavioralEnabled, subThoughtsEnabled);
            case "characterDrives" -> new CognitionConfig(moodEnabled, drivesEnabled, mentalModelEnabled, userModelEnabled, strategyEnabled, narrativeEnabled, goalsEnabled, memoryHygieneEnabled, innerLifeEnabled, directivePrompts, enabled, needsPyramidEnabled, attentionEnabled, temporalFocusEnabled, reflectionEnabled, consolidationEnabled, entityKnowledgeEnabled, perspectiveComparisonEnabled, domainActivationEnabled, appraisalEnabled, salienceEnabled, behavioralEnabled, subThoughtsEnabled);
            case "needsPyramid" -> new CognitionConfig(moodEnabled, drivesEnabled, mentalModelEnabled, userModelEnabled, strategyEnabled, narrativeEnabled, goalsEnabled, memoryHygieneEnabled, innerLifeEnabled, directivePrompts, characterDrivesEnabled, enabled, attentionEnabled, temporalFocusEnabled, reflectionEnabled, consolidationEnabled, entityKnowledgeEnabled, perspectiveComparisonEnabled, domainActivationEnabled, appraisalEnabled, salienceEnabled, behavioralEnabled, subThoughtsEnabled);
            case "attention" -> new CognitionConfig(moodEnabled, drivesEnabled, mentalModelEnabled, userModelEnabled, strategyEnabled, narrativeEnabled, goalsEnabled, memoryHygieneEnabled, innerLifeEnabled, directivePrompts, characterDrivesEnabled, needsPyramidEnabled, enabled, temporalFocusEnabled, reflectionEnabled, consolidationEnabled, entityKnowledgeEnabled, perspectiveComparisonEnabled, domainActivationEnabled, appraisalEnabled, salienceEnabled, behavioralEnabled, subThoughtsEnabled);
            case "temporalFocus" -> new CognitionConfig(moodEnabled, drivesEnabled, mentalModelEnabled, userModelEnabled, strategyEnabled, narrativeEnabled, goalsEnabled, memoryHygieneEnabled, innerLifeEnabled, directivePrompts, characterDrivesEnabled, needsPyramidEnabled, attentionEnabled, enabled, reflectionEnabled, consolidationEnabled, entityKnowledgeEnabled, perspectiveComparisonEnabled, domainActivationEnabled, appraisalEnabled, salienceEnabled, behavioralEnabled, subThoughtsEnabled);
            case "reflection" -> new CognitionConfig(moodEnabled, drivesEnabled, mentalModelEnabled, userModelEnabled, strategyEnabled, narrativeEnabled, goalsEnabled, memoryHygieneEnabled, innerLifeEnabled, directivePrompts, characterDrivesEnabled, needsPyramidEnabled, attentionEnabled, temporalFocusEnabled, enabled, consolidationEnabled, entityKnowledgeEnabled, perspectiveComparisonEnabled, domainActivationEnabled, appraisalEnabled, salienceEnabled, behavioralEnabled, subThoughtsEnabled);
            case "consolidation" -> new CognitionConfig(moodEnabled, drivesEnabled, mentalModelEnabled, userModelEnabled, strategyEnabled, narrativeEnabled, goalsEnabled, memoryHygieneEnabled, innerLifeEnabled, directivePrompts, characterDrivesEnabled, needsPyramidEnabled, attentionEnabled, temporalFocusEnabled, reflectionEnabled, enabled, entityKnowledgeEnabled, perspectiveComparisonEnabled, domainActivationEnabled, appraisalEnabled, salienceEnabled, behavioralEnabled, subThoughtsEnabled);
            case "entityKnowledge" -> new CognitionConfig(moodEnabled, drivesEnabled, mentalModelEnabled, userModelEnabled, strategyEnabled, narrativeEnabled, goalsEnabled, memoryHygieneEnabled, innerLifeEnabled, directivePrompts, characterDrivesEnabled, needsPyramidEnabled, attentionEnabled, temporalFocusEnabled, reflectionEnabled, consolidationEnabled, enabled, perspectiveComparisonEnabled, domainActivationEnabled, appraisalEnabled, salienceEnabled, behavioralEnabled, subThoughtsEnabled);
            case "perspectiveComparison" -> new CognitionConfig(moodEnabled, drivesEnabled, mentalModelEnabled, userModelEnabled, strategyEnabled, narrativeEnabled, goalsEnabled, memoryHygieneEnabled, innerLifeEnabled, directivePrompts, characterDrivesEnabled, needsPyramidEnabled, attentionEnabled, temporalFocusEnabled, reflectionEnabled, consolidationEnabled, entityKnowledgeEnabled, enabled, domainActivationEnabled, appraisalEnabled, salienceEnabled, behavioralEnabled, subThoughtsEnabled);
            case "domainActivation" -> new CognitionConfig(moodEnabled, drivesEnabled, mentalModelEnabled, userModelEnabled, strategyEnabled, narrativeEnabled, goalsEnabled, memoryHygieneEnabled, innerLifeEnabled, directivePrompts, characterDrivesEnabled, needsPyramidEnabled, attentionEnabled, temporalFocusEnabled, reflectionEnabled, consolidationEnabled, entityKnowledgeEnabled, perspectiveComparisonEnabled, enabled, appraisalEnabled, salienceEnabled, behavioralEnabled, subThoughtsEnabled);
            case "appraisal" -> new CognitionConfig(moodEnabled, drivesEnabled, mentalModelEnabled, userModelEnabled, strategyEnabled, narrativeEnabled, goalsEnabled, memoryHygieneEnabled, innerLifeEnabled, directivePrompts, characterDrivesEnabled, needsPyramidEnabled, attentionEnabled, temporalFocusEnabled, reflectionEnabled, consolidationEnabled, entityKnowledgeEnabled, perspectiveComparisonEnabled, domainActivationEnabled, enabled, salienceEnabled, behavioralEnabled, subThoughtsEnabled);
            case "salience" -> new CognitionConfig(moodEnabled, drivesEnabled, mentalModelEnabled, userModelEnabled, strategyEnabled, narrativeEnabled, goalsEnabled, memoryHygieneEnabled, innerLifeEnabled, directivePrompts, characterDrivesEnabled, needsPyramidEnabled, attentionEnabled, temporalFocusEnabled, reflectionEnabled, consolidationEnabled, entityKnowledgeEnabled, perspectiveComparisonEnabled, domainActivationEnabled, appraisalEnabled, enabled, behavioralEnabled, subThoughtsEnabled);
            case "behavioral" -> new CognitionConfig(moodEnabled, drivesEnabled, mentalModelEnabled, userModelEnabled, strategyEnabled, narrativeEnabled, goalsEnabled, memoryHygieneEnabled, innerLifeEnabled, directivePrompts, characterDrivesEnabled, needsPyramidEnabled, attentionEnabled, temporalFocusEnabled, reflectionEnabled, consolidationEnabled, entityKnowledgeEnabled, perspectiveComparisonEnabled, domainActivationEnabled, appraisalEnabled, salienceEnabled, enabled, subThoughtsEnabled);
            case "subThoughts" -> new CognitionConfig(moodEnabled, drivesEnabled, mentalModelEnabled, userModelEnabled, strategyEnabled, narrativeEnabled, goalsEnabled, memoryHygieneEnabled, innerLifeEnabled, directivePrompts, characterDrivesEnabled, needsPyramidEnabled, attentionEnabled, temporalFocusEnabled, reflectionEnabled, consolidationEnabled, entityKnowledgeEnabled, perspectiveComparisonEnabled, domainActivationEnabled, appraisalEnabled, salienceEnabled, behavioralEnabled, enabled);
            default -> throw new IllegalArgumentException("Unknown subsystem: " + subsystem);
        };
    }

    public CognitionConfig without(String... subsystems) {
        var config = this;
        for (var s : subsystems) {
            config = config.with(s, false);
        }
        return config;
    }
}

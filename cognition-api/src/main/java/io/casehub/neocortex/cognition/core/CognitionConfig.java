package io.casehub.neocortex.cognition.core;

public final class CognitionConfig {

    public enum Subsystem {
        MOOD, DRIVES, MENTAL_MODEL, USER_MODEL, STRATEGY, NARRATIVE, GOALS,
        MEMORY_HYGIENE, INNER_LIFE, DIRECTIVE_PROMPTS, CHARACTER_DRIVES,
        NEEDS_PYRAMID, ATTENTION, TEMPORAL_FOCUS, REFLECTION, CONSOLIDATION,
        ENTITY_KNOWLEDGE, PERSPECTIVE_COMPARISON, DOMAIN_ACTIVATION,
        APPRAISAL, SALIENCE, BEHAVIORAL, SUB_THOUGHTS, OTHER_MODEL,
        REGULATION, COPING, SITUATIONAL_PERSONALITY, DISSONANCE
    }

    private static final java.util.Map<String, Subsystem> NAME_TO_SUBSYSTEM = java.util.Map.ofEntries(
            java.util.Map.entry("mood", Subsystem.MOOD),
            java.util.Map.entry("drives", Subsystem.DRIVES),
            java.util.Map.entry("mentalModel", Subsystem.MENTAL_MODEL),
            java.util.Map.entry("userModel", Subsystem.USER_MODEL),
            java.util.Map.entry("strategy", Subsystem.STRATEGY),
            java.util.Map.entry("narrative", Subsystem.NARRATIVE),
            java.util.Map.entry("goals", Subsystem.GOALS),
            java.util.Map.entry("memoryHygiene", Subsystem.MEMORY_HYGIENE),
            java.util.Map.entry("innerLife", Subsystem.INNER_LIFE),
            java.util.Map.entry("directivePrompts", Subsystem.DIRECTIVE_PROMPTS),
            java.util.Map.entry("characterDrives", Subsystem.CHARACTER_DRIVES),
            java.util.Map.entry("needsPyramid", Subsystem.NEEDS_PYRAMID),
            java.util.Map.entry("attention", Subsystem.ATTENTION),
            java.util.Map.entry("temporalFocus", Subsystem.TEMPORAL_FOCUS),
            java.util.Map.entry("reflection", Subsystem.REFLECTION),
            java.util.Map.entry("consolidation", Subsystem.CONSOLIDATION),
            java.util.Map.entry("entityKnowledge", Subsystem.ENTITY_KNOWLEDGE),
            java.util.Map.entry("perspectiveComparison", Subsystem.PERSPECTIVE_COMPARISON),
            java.util.Map.entry("domainActivation", Subsystem.DOMAIN_ACTIVATION),
            java.util.Map.entry("appraisal", Subsystem.APPRAISAL),
            java.util.Map.entry("salience", Subsystem.SALIENCE),
            java.util.Map.entry("behavioral", Subsystem.BEHAVIORAL),
            java.util.Map.entry("subThoughts", Subsystem.SUB_THOUGHTS),
            java.util.Map.entry("otherModel", Subsystem.OTHER_MODEL),
            java.util.Map.entry("regulation", Subsystem.REGULATION),
            java.util.Map.entry("coping", Subsystem.COPING),
            java.util.Map.entry("situationalPersonality", Subsystem.SITUATIONAL_PERSONALITY),
            java.util.Map.entry("dissonance", Subsystem.DISSONANCE)
                                                                                                     );

    private final java.util.EnumSet<Subsystem> enabled;

    private CognitionConfig(java.util.EnumSet<Subsystem> enabled) {
        this.enabled = java.util.EnumSet.copyOf(enabled);
    }

    private CognitionConfig(boolean empty) {
        this.enabled = java.util.EnumSet.noneOf(Subsystem.class);
    }

    public boolean isEnabled(Subsystem subsystem) {
        return enabled.contains(subsystem);
    }

    public java.util.Set<Subsystem> enabledSubsystems() {
        return java.util.Collections.unmodifiableSet(enabled);
    }

    // --- Backward-compatible accessors ---
    public boolean moodEnabled() {return isEnabled(Subsystem.MOOD);}

    public boolean drivesEnabled()                 {return isEnabled(Subsystem.DRIVES);}

    public boolean mentalModelEnabled()            {return isEnabled(Subsystem.MENTAL_MODEL);}

    public boolean userModelEnabled()              {return isEnabled(Subsystem.USER_MODEL);}

    public boolean strategyEnabled()               {return isEnabled(Subsystem.STRATEGY);}

    public boolean narrativeEnabled()              {return isEnabled(Subsystem.NARRATIVE);}

    public boolean goalsEnabled()                  {return isEnabled(Subsystem.GOALS);}

    public boolean memoryHygieneEnabled()          {return isEnabled(Subsystem.MEMORY_HYGIENE);}

    public boolean innerLifeEnabled()              {return isEnabled(Subsystem.INNER_LIFE);}

    public boolean directivePrompts()              {return isEnabled(Subsystem.DIRECTIVE_PROMPTS);}

    public boolean characterDrivesEnabled()        {return isEnabled(Subsystem.CHARACTER_DRIVES);}

    public boolean needsPyramidEnabled()           {return isEnabled(Subsystem.NEEDS_PYRAMID);}

    public boolean attentionEnabled()              {return isEnabled(Subsystem.ATTENTION);}

    public boolean temporalFocusEnabled()          {return isEnabled(Subsystem.TEMPORAL_FOCUS);}

    public boolean reflectionEnabled()             {return isEnabled(Subsystem.REFLECTION);}

    public boolean consolidationEnabled()          {return isEnabled(Subsystem.CONSOLIDATION);}

    public boolean entityKnowledgeEnabled()        {return isEnabled(Subsystem.ENTITY_KNOWLEDGE);}

    public boolean perspectiveComparisonEnabled()  {return isEnabled(Subsystem.PERSPECTIVE_COMPARISON);}

    public boolean domainActivationEnabled()       {return isEnabled(Subsystem.DOMAIN_ACTIVATION);}

    public boolean appraisalEnabled()              {return isEnabled(Subsystem.APPRAISAL);}

    public boolean salienceEnabled()               {return isEnabled(Subsystem.SALIENCE);}

    public boolean behavioralEnabled()             {return isEnabled(Subsystem.BEHAVIORAL);}

    public boolean subThoughtsEnabled()            {return isEnabled(Subsystem.SUB_THOUGHTS);}

    public boolean otherModelEnabled()             {return isEnabled(Subsystem.OTHER_MODEL);}

    public boolean regulationEnabled()             {return isEnabled(Subsystem.REGULATION);}

    public boolean copingEnabled()                 {return isEnabled(Subsystem.COPING);}

    public boolean situationalPersonalityEnabled() {return isEnabled(Subsystem.SITUATIONAL_PERSONALITY);}

    public boolean dissonanceEnabled()             {return isEnabled(Subsystem.DISSONANCE);}

    // --- Factory methods ---

    public static CognitionConfig all() {
        return new CognitionConfig(java.util.EnumSet.allOf(Subsystem.class));
    }

    @Deprecated
    public static CognitionConfig withAllSubsystems() {
        return all();
    }

    public static CognitionConfig none() {
        return new CognitionConfig(false);
    }

    // --- Mutation (returns new instance) ---

    public CognitionConfig withDirectives() {
        return with(Subsystem.DIRECTIVE_PROMPTS);
    }

    public CognitionConfig with(Subsystem subsystem) {
        var copy = java.util.EnumSet.copyOf(enabled);
        copy.add(subsystem);
        return new CognitionConfig(copy);
    }

    public CognitionConfig without(Subsystem subsystem) {
        var copy = java.util.EnumSet.copyOf(enabled);
        copy.remove(subsystem);
        return new CognitionConfig(copy);
    }

    public CognitionConfig with(String subsystem, boolean enabled) {
        var s = NAME_TO_SUBSYSTEM.get(subsystem);
        if (s == null) {throw new IllegalArgumentException("Unknown subsystem: " + subsystem);}
        return enabled ? with(s) : without(s);
    }

    public CognitionConfig without(String... subsystems) {
        var config = this;
        for (var s : subsystems) {
            config = config.with(s, false);
        }
        return config;
    }

    @Override
    public boolean equals(Object o) {
        return this == o || (o instanceof CognitionConfig c && enabled.equals(c.enabled));
    }

    @Override
    public int hashCode() {
        return enabled.hashCode();
    }

    @Override
    public String toString() {
        return "CognitionConfig" + enabled;
    }
}

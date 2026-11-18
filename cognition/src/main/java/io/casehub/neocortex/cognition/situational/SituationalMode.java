package io.casehub.neocortex.cognition.situational;

public enum SituationalMode {
    STRESS(Group.AFFECTIVE),
    FLOW(Group.AFFECTIVE),
    SOCIAL_COLLABORATIVE(Group.SOCIAL),
    SOCIAL_COMPETITIVE(Group.SOCIAL),
    SOCIAL_HIERARCHICAL(Group.SOCIAL);

    public enum Group { AFFECTIVE, SOCIAL }

    private final Group group;

    SituationalMode(Group group) {
        this.group = group;
    }

    public Group group() {
        return group;
    }
}

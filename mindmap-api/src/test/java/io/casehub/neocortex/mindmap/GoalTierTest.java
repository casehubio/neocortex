package io.casehub.neocortex.mindmap;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class GoalTierTest {

    @Test
    void aspirationalMapsToThematic() {
        assertEquals(GoalTier.THEMATIC, GoalTier.fromHorizon("aspirational"));
    }

    @Test
    void mediumMapsToStrategic() {
        assertEquals(GoalTier.STRATEGIC, GoalTier.fromHorizon("medium"));
    }

    @Test
    void longMapsToStrategic() {
        assertEquals(GoalTier.STRATEGIC, GoalTier.fromHorizon("long"));
    }

    @Test
    void immediateMapsToTactical() {
        assertEquals(GoalTier.TACTICAL, GoalTier.fromHorizon("immediate"));
    }

    @Test
    void shortMapsToTactical() {
        assertEquals(GoalTier.TACTICAL, GoalTier.fromHorizon("short"));
    }

    @Test
    void nullDefaultsToTactical() {
        assertEquals(GoalTier.TACTICAL, GoalTier.fromHorizon(null));
    }

    @Test
    void unknownDefaultsToTactical() {
        assertEquals(GoalTier.TACTICAL, GoalTier.fromHorizon("unknown"));
    }
}

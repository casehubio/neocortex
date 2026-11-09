package io.casehub.neocortex.cognition.regulation;

import io.casehub.neocortex.cognition.drive.DriveAxis;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EmotionRegulationResultTest {

    @Test
    void internalStrategyHasNoEnvironmentalFields() {
        var result = new EmotionRegulationResult(
                EmotionRegulationStrategy.RESPONSE_MODULATION,
                List.of("anger"), List.of("gloating"),
                0.85, 0.4, 0.6,
                "You hold back the triumph", null, null);
        assertFalse(result.isEnvironmentalStrategy());
        assertNull(result.regulationAxis());
        assertNull(result.environmentalDirective());
    }

    @Test
    void environmentalStrategyHasAxisAndDirective() {
        var result = new EmotionRegulationResult(
                EmotionRegulationStrategy.SITUATION_SELECTION,
                List.of("anxiety"), List.of(),
                0.9, 0.5, 0.7,
                "You decide to leave", DriveAxis.AFFILIATION,
                "Avoid being alone with Muttley");
        assertTrue(result.isEnvironmentalStrategy());
        assertEquals(DriveAxis.AFFILIATION, result.regulationAxis());
        assertEquals("Avoid being alone with Muttley", result.environmentalDirective());
    }

    @Test
    void situationModificationIsAlsoEnvironmental() {
        var result = new EmotionRegulationResult(
                EmotionRegulationStrategy.SITUATION_MODIFICATION,
                List.of("frustration"), List.of("competence"),
                0.75, 0.5, 0.5,
                "You try to change the situation", DriveAxis.COMPETENCE,
                "Redirect the conversation");
        assertTrue(result.isEnvironmentalStrategy());
    }

    @Test
    void attentionalDeploymentIsInternal() {
        var result = new EmotionRegulationResult(
                EmotionRegulationStrategy.ATTENTIONAL_DEPLOYMENT,
                List.of("anger"), List.of(),
                0.8, 0.3, 0.7,
                "You focus on something else", null, null);
        assertFalse(result.isEnvironmentalStrategy());
    }

    @Test
    void cognitiveChangeIsInternal() {
        var result = new EmotionRegulationResult(
                EmotionRegulationStrategy.COGNITIVE_CHANGE,
                List.of("sadness"), List.of(),
                0.7, 0.4, 0.5,
                "You reframe the meaning", null, null);
        assertFalse(result.isEnvironmentalStrategy());
    }

    @Test
    void allTenStrategiesExist() {
        assertEquals(10, EmotionRegulationStrategy.values().length);
    }

    @Test
    void isSocialStrategy_trueForSocialStrategies() {
        var result = new EmotionRegulationResult(
                EmotionRegulationStrategy.SOCIAL_SUPPORT_SEEKING,
                List.of("ANXIETY"), List.of(), 0.8, 0.4, 0.6,
                "You seek comfort", null, null, "agent-b");
        assertTrue(result.isSocialStrategy());
        assertFalse(result.isExtrinsicStrategy());
    }

    @Test
    void isExtrinsicStrategy_trueForCoRegulation() {
        var result = new EmotionRegulationResult(
                EmotionRegulationStrategy.CO_REGULATION,
                List.of("EMPATHY"), List.of(), 0.7, 0.3, 0.7,
                "You comfort them", null, null, "agent-c");
        assertTrue(result.isExtrinsicStrategy());
        assertTrue(result.isSocialStrategy());
    }

    @Test
    void intrapersonalStrategy_notSocial() {
        var result = new EmotionRegulationResult(
                EmotionRegulationStrategy.COGNITIVE_CHANGE,
                List.of("ANGER"), List.of(), 0.8, 0.4, 0.6,
                "You reframe", null, null, null);
        assertFalse(result.isSocialStrategy());
        assertFalse(result.isExtrinsicStrategy());
    }

    @Test
    void targetAgentIdAccessible() {
        var result = new EmotionRegulationResult(
                EmotionRegulationStrategy.SOCIAL_SUPPORT_SEEKING,
                List.of("ANXIETY"), List.of(), 0.8, 0.4, 0.6,
                "You seek comfort", null, null, "penelope-pitstop");
        assertEquals("penelope-pitstop", result.targetAgentId());
    }

    @Test
    void existingConstructorPassesNullTargetAgentId() {
        var result = new EmotionRegulationResult(
                EmotionRegulationStrategy.COGNITIVE_CHANGE,
                List.of("ANGER"), List.of(), 0.8, 0.4, 0.6,
                "You reframe", null, null);
        assertNull(result.targetAgentId());
    }

}

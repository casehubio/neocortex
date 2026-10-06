package io.casehub.neocortex.mindmap.intelligence.consolidation;

import io.casehub.neocortex.cognitive.EmotionType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import static org.assertj.core.api.Assertions.assertThat;

class EmotionReinforcementMapperTest {

    @ParameterizedTest
    @EnumSource(EmotionType.class)
    void everyEmotionTypeHasMapping(EmotionType type) {
        var signal = EmotionReinforcementMapper.forEmotion(type);
        assertThat(signal).isNotNull();
        assertThat(signal.lambdaSign()).isIn(-1.0, 1.0);
        assertThat(signal.pathwayTags()).isNotEmpty();
    }

    @Test
    void fearMapsToNegativeBisAndThreat() {
        var signal = EmotionReinforcementMapper.forEmotion(EmotionType.FEAR);
        assertThat(signal.lambdaSign()).isEqualTo(-1.0);
        assertThat(signal.pathwayTags()).containsExactlyInAnyOrder("bis", "threat");
    }

    @Test
    void fearsConfirmedMapsToNegativeBisAndThreat() {
        var signal = EmotionReinforcementMapper.forEmotion(EmotionType.FEARS_CONFIRMED);
        assertThat(signal.lambdaSign()).isEqualTo(-1.0);
        assertThat(signal.pathwayTags()).containsExactlyInAnyOrder("bis", "threat");
    }

    @Test
    void hopeMapsToPositiveBas() {
        var signal = EmotionReinforcementMapper.forEmotion(EmotionType.HOPE);
        assertThat(signal.lambdaSign()).isEqualTo(1.0);
        assertThat(signal.pathwayTags()).containsExactlyInAnyOrder("bas");
    }

    @Test
    void reliefMapsToPositiveBas() {
        var signal = EmotionReinforcementMapper.forEmotion(EmotionType.RELIEF);
        assertThat(signal.lambdaSign()).isEqualTo(1.0);
        assertThat(signal.pathwayTags()).containsExactlyInAnyOrder("bas");
    }

    @Test
    void joyMapsToPositiveBas() {
        var signal = EmotionReinforcementMapper.forEmotion(EmotionType.JOY);
        assertThat(signal.lambdaSign()).isEqualTo(1.0);
        assertThat(signal.pathwayTags()).containsExactlyInAnyOrder("bas");
    }

    @Test
    void satisfactionMapsToPositiveBas() {
        var signal = EmotionReinforcementMapper.forEmotion(EmotionType.SATISFACTION);
        assertThat(signal.lambdaSign()).isEqualTo(1.0);
        assertThat(signal.pathwayTags()).containsExactlyInAnyOrder("bas");
    }

    @Test
    void gratificationMapsToPositiveBas() {
        var signal = EmotionReinforcementMapper.forEmotion(EmotionType.GRATIFICATION);
        assertThat(signal.lambdaSign()).isEqualTo(1.0);
        assertThat(signal.pathwayTags()).containsExactlyInAnyOrder("bas");
    }

    @Test
    void distressMapsToNegativeBis() {
        var signal = EmotionReinforcementMapper.forEmotion(EmotionType.DISTRESS);
        assertThat(signal.lambdaSign()).isEqualTo(-1.0);
        assertThat(signal.pathwayTags()).containsExactlyInAnyOrder("bis");
    }

    @Test
    void disappointmentMapsToNegativeBis() {
        var signal = EmotionReinforcementMapper.forEmotion(EmotionType.DISAPPOINTMENT);
        assertThat(signal.lambdaSign()).isEqualTo(-1.0);
        assertThat(signal.pathwayTags()).containsExactlyInAnyOrder("bis");
    }

    @Test
    void prideMapsToPositiveBas() {
        var signal = EmotionReinforcementMapper.forEmotion(EmotionType.PRIDE);
        assertThat(signal.lambdaSign()).isEqualTo(1.0);
        assertThat(signal.pathwayTags()).containsExactlyInAnyOrder("bas");
    }

    @Test
    void admirationMapsToPositiveBas() {
        var signal = EmotionReinforcementMapper.forEmotion(EmotionType.ADMIRATION);
        assertThat(signal.lambdaSign()).isEqualTo(1.0);
        assertThat(signal.pathwayTags()).containsExactlyInAnyOrder("bas");
    }

    @Test
    void shameMapsToNegativeCompliance() {
        var signal = EmotionReinforcementMapper.forEmotion(EmotionType.SHAME);
        assertThat(signal.lambdaSign()).isEqualTo(-1.0);
        assertThat(signal.pathwayTags()).containsExactlyInAnyOrder("compliance");
    }

    @Test
    void reproachMapsToNegativeCompliance() {
        var signal = EmotionReinforcementMapper.forEmotion(EmotionType.REPROACH);
        assertThat(signal.lambdaSign()).isEqualTo(-1.0);
        assertThat(signal.pathwayTags()).containsExactlyInAnyOrder("compliance");
    }

    @Test
    void angerMapsToNegativeFightAssert() {
        var signal = EmotionReinforcementMapper.forEmotion(EmotionType.ANGER);
        assertThat(signal.lambdaSign()).isEqualTo(-1.0);
        assertThat(signal.pathwayTags()).containsExactlyInAnyOrder("fight_assert");
    }

    @Test
    void resentmentMapsToNegativeFightAssert() {
        var signal = EmotionReinforcementMapper.forEmotion(EmotionType.RESENTMENT);
        assertThat(signal.lambdaSign()).isEqualTo(-1.0);
        assertThat(signal.pathwayTags()).containsExactlyInAnyOrder("fight_assert");
    }

    @Test
    void loveMapsToPositiveBasAndFawn() {
        var signal = EmotionReinforcementMapper.forEmotion(EmotionType.LOVE);
        assertThat(signal.lambdaSign()).isEqualTo(1.0);
        assertThat(signal.pathwayTags()).containsExactlyInAnyOrder("bas", "fawn_accommodate");
    }

    @Test
    void happyForMapsToPositiveBasAndFawn() {
        var signal = EmotionReinforcementMapper.forEmotion(EmotionType.HAPPY_FOR);
        assertThat(signal.lambdaSign()).isEqualTo(1.0);
        assertThat(signal.pathwayTags()).containsExactlyInAnyOrder("bas", "fawn_accommodate");
    }

    @Test
    void gratitudeMapsToPositiveBasAndFawn() {
        var signal = EmotionReinforcementMapper.forEmotion(EmotionType.GRATITUDE);
        assertThat(signal.lambdaSign()).isEqualTo(1.0);
        assertThat(signal.pathwayTags()).containsExactlyInAnyOrder("bas", "fawn_accommodate");
    }

    @Test
    void hateMapsToNegativeThreat() {
        var signal = EmotionReinforcementMapper.forEmotion(EmotionType.HATE);
        assertThat(signal.lambdaSign()).isEqualTo(-1.0);
        assertThat(signal.pathwayTags()).containsExactlyInAnyOrder("threat");
    }

    @Test
    void gloatingMapsToNegativeThreat() {
        var signal = EmotionReinforcementMapper.forEmotion(EmotionType.GLOATING);
        assertThat(signal.lambdaSign()).isEqualTo(-1.0);
        assertThat(signal.pathwayTags()).containsExactlyInAnyOrder("threat");
    }

    @Test
    void pityMapsToPositiveFawn() {
        var signal = EmotionReinforcementMapper.forEmotion(EmotionType.PITY);
        assertThat(signal.lambdaSign()).isEqualTo(1.0);
        assertThat(signal.pathwayTags()).containsExactlyInAnyOrder("fawn_accommodate");
    }

    @Test
    void remorseMapsToNegativeCompliance() {
        var signal = EmotionReinforcementMapper.forEmotion(EmotionType.REMORSE);
        assertThat(signal.lambdaSign()).isEqualTo(-1.0);
        assertThat(signal.pathwayTags()).containsExactlyInAnyOrder("compliance");
    }
}

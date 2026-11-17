package io.casehub.neocortex.cognitive.index;

import io.casehub.neocortex.mindmap.CuriosityConfig;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class BehavioralDefaultsTest {

    @Test
    void empty_allFieldsNull() {
        var empty = BehavioralDefaults.empty();
        assertThat(empty.curiosity()).isNull();
        assertThat(empty.temporalFocus()).isNull();
        assertThat(empty.socialCognition()).isNull();
        assertThat(empty.cbrStrategy()).isNull();
    }

    @Test
    void mergeWith_explicitWins() {
        var explicitCuriosity = CuriosityConfig.defaults();
        var derivedSocial = SocialCognitionDefaults.defaults();

        var explicit = new BehavioralDefaults(explicitCuriosity, null, null, null);
        var derived = new BehavioralDefaults(null, null, derivedSocial, null);

        var merged = explicit.mergeWith(derived);
        assertThat(merged.curiosity()).isSameAs(explicitCuriosity);
        assertThat(merged.socialCognition()).isSameAs(derivedSocial);
    }

    @Test
    void mergeWith_derivedNull_returnsThis() {
        var explicit = new BehavioralDefaults(CuriosityConfig.defaults(), null, null, null);
        var merged = explicit.mergeWith(null);
        assertThat(merged).isSameAs(explicit);
    }
}

package io.casehub.neocortex.cognitive.index;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class AttachmentProfileTest {

    @Test
    void secure_hasLowAnxietyAndAvoidance() {
        var profile = new AttachmentProfile("secure", 0.1, 0.1);
        assertThat(profile.style()).isEqualTo("secure");
        assertThat(profile.anxiety()).isEqualTo(0.1);
        assertThat(profile.avoidance()).isEqualTo(0.1);
    }

    @Test
    void dismissiveAvoidant_hasHighAvoidance() {
        var profile = new AttachmentProfile("dismissive-avoidant", 0.1, 0.8);
        assertThat(profile.avoidance()).isEqualTo(0.8);
    }

    @Test
    void anxietyOutOfRange_throws() {
        assertThatThrownBy(() -> new AttachmentProfile("secure", -0.1, 0.0))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new AttachmentProfile("secure", 1.1, 0.0))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void avoidanceOutOfRange_throws() {
        assertThatThrownBy(() -> new AttachmentProfile("secure", 0.0, -0.1))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new AttachmentProfile("secure", 0.0, 1.1))
                .isInstanceOf(IllegalArgumentException.class);
    }
}

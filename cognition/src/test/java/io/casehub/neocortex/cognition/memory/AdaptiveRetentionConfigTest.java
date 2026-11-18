package io.casehub.neocortex.cognition.memory;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

class AdaptiveRetentionConfigTest {

    @Test
    void startsWithDefault() {
        var holder = new AdaptiveRetentionConfig();
        assertThat(holder.current()).isEqualTo(RetentionConfig.DEFAULT);
    }

    @Test
    void updateSwapsConfig() {
        var holder = new AdaptiveRetentionConfig();
        var custom = new RetentionConfig(0.2, 1.0, 1.0, 0.5, 0.5);
        holder.update(custom);
        assertThat(holder.current()).isEqualTo(custom);
    }

    @Test
    void updateIsVisibleAcrossReads() {
        var holder = new AdaptiveRetentionConfig();
        var c1 = new RetentionConfig(0.15, 1.0, 1.0, 0.5, 0.5);
        var c2 = new RetentionConfig(0.25, 1.0, 1.0, 0.5, 0.5);
        holder.update(c1);
        assertThat(holder.current().retentionThreshold()).isEqualTo(0.15);
        holder.update(c2);
        assertThat(holder.current().retentionThreshold()).isEqualTo(0.25);
    }
}

package io.casehub.neocortex.memory;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MemoryRetentionPolicyTest {

    private static final MemoryDomain CBR = new MemoryDomain("cbr");

    @Test void validPolicy_ageBased() {
        var policy = new MemoryRetentionPolicy("t1", CBR, 30, null);
        assertThat(policy.maxAgeDays()).isEqualTo(30);
        assertThat(policy.minImportance()).isNull();
    }

    @Test void validPolicy_importanceBased() {
        var policy = new MemoryRetentionPolicy("t1", CBR, null, 0.3);
        assertThat(policy.minImportance()).isEqualTo(0.3);
    }

    @Test void validPolicy_combined() {
        var policy = new MemoryRetentionPolicy("t1", CBR, 180, 0.2);
        assertThat(policy.maxAgeDays()).isEqualTo(180);
        assertThat(policy.minImportance()).isEqualTo(0.2);
    }

    @Test void rejectsBothNull() {
        assertThatThrownBy(() -> new MemoryRetentionPolicy("t1", CBR, null, null))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("at least one");
    }

    @Test void rejectsNullTenantId() {
        assertThatThrownBy(() -> new MemoryRetentionPolicy(null, CBR, 30, null))
            .isInstanceOf(NullPointerException.class);
    }

    @Test void rejectsNullDomain() {
        assertThatThrownBy(() -> new MemoryRetentionPolicy("t1", null, 30, null))
            .isInstanceOf(NullPointerException.class);
    }

    @Test void minImportance_outOfRange_throws() {
        assertThatThrownBy(() -> new MemoryRetentionPolicy("t1", CBR, null, 1.5))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("[0, 1]");
        assertThatThrownBy(() -> new MemoryRetentionPolicy("t1", CBR, null, -0.1))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("[0, 1]");
    }

    @Test void rejectsNonPositiveMaxAgeDays() {
        assertThatThrownBy(() -> new MemoryRetentionPolicy("t1", CBR, 0, null))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("positive");
    }
}

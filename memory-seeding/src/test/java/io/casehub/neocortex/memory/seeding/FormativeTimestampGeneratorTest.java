package io.casehub.neocortex.memory.seeding;

import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class FormativeTimestampGeneratorTest {

    @Test
    void infancyBeforeChildhood() {
        var inf = FormativeTimestampGenerator.generate(1, "infancy", Instant.EPOCH);
        var ch = FormativeTimestampGenerator.generate(1, "childhood", Instant.EPOCH);
        assertThat(inf.getFirst()).isBefore(ch.getFirst());
    }

    @Test
    void childhoodBeforeAdolescence() {
        var ch = FormativeTimestampGenerator.generate(1, "childhood", Instant.EPOCH);
        var ad = FormativeTimestampGenerator.generate(1, "adolescence", Instant.EPOCH);
        assertThat(ch.getFirst()).isBefore(ad.getFirst());
    }

    @Test
    void deterministic() {
        var a = FormativeTimestampGenerator.generate(5, "infancy", Instant.EPOCH);
        var b = FormativeTimestampGenerator.generate(5, "infancy", Instant.EPOCH);
        assertThat(a).isEqualTo(b);
    }

    @Test
    void correctCount() {
        var ts = FormativeTimestampGenerator.generate(10, "childhood", Instant.EPOCH);
        assertThat(ts).hasSize(10);
    }

    @Test
    void singleEventNoSpacing() {
        var ts = FormativeTimestampGenerator.generate(1, "adult", Instant.EPOCH);
        assertThat(ts).hasSize(1);
    }

    @Test
    void zeroCountEmpty() {
        assertThat(FormativeTimestampGenerator.generate(0, "infancy", Instant.EPOCH)).isEmpty();
    }
}

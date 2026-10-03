package io.casehub.neocortex.memory.seeding;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class SalienceDefaultsTest {
    @Test void infancy()     { assertThat(SalienceDefaults.forPeriod("infancy")).isEqualTo(3.0); }
    @Test void childhood()   { assertThat(SalienceDefaults.forPeriod("childhood")).isEqualTo(2.0); }
    @Test void adolescence() { assertThat(SalienceDefaults.forPeriod("adolescence")).isEqualTo(1.5); }
    @Test void adult()       { assertThat(SalienceDefaults.forPeriod("adult")).isEqualTo(1.0); }
    @Test void nullPeriod()  { assertThat(SalienceDefaults.forPeriod(null)).isEqualTo(1.0); }
}

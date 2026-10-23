package io.quarkmind.inference.bayes.llm;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ElicitorConfigTest {

    @Test
    void defaultsHaveExpectedValues() {
        var config = ElicitorConfig.defaults();
        assertThat(config.modelHint()).isNull();
        assertThat(config.temperature()).isEqualTo(0.3);
        assertThat(config.maxRetries()).isEqualTo(1);
    }
}

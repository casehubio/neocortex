package io.casehub.neocortex.cognitive.observability;

import io.casehub.neocortex.cognitive.CognitiveCapacityConfig;
import io.casehub.neocortex.cognitive.index.CognitiveDefaults;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class CapacityModulationReportTest {

    @Test
    void fromDefaults_producesAllSixSites() {
        var defaults = CognitiveDefaults.empty("test-agent")
                .withCapacityConfig(new CognitiveCapacityConfig(0.6, 0.8, 0.5));

        var report = CapacityModulationReport.from(defaults);

        assertThat(report.agentId()).isEqualTo("test-agent");
        assertThat(report.sites()).hasSize(6);
        assertThat(report.sites().stream().map(CapacityModulationReport.SiteModulation::site))
                .containsExactly("caps-alpha", "cbr-outcome-lr", "graduation-score",
                        "drive-adaptation-lr", "strategy-delta-ceiling", "personality-l2-ceiling");
    }

    @Test
    void capsAlpha_usesAssociativeReach() {
        var defaults = CognitiveDefaults.empty("agent-1")
                .withCapacityConfig(new CognitiveCapacityConfig(0.9, 0.4, 0.7));

        var report = CapacityModulationReport.from(defaults);
        var capsAlpha = report.sites().stream()
                .filter(s -> "caps-alpha".equals(s.site())).findFirst().orElseThrow();

        assertThat(capsAlpha.capacityDimension()).isEqualTo("associativeReach");
        assertThat(capsAlpha.capacityValue()).isEqualTo(0.4);
        assertThat(capsAlpha.effect()).isEqualTo("multiplier");
    }

    @Test
    void graduationScore_usesRelationalComplexity() {
        var defaults = CognitiveDefaults.empty("agent-2")
                .withCapacityConfig(new CognitiveCapacityConfig(0.3, 0.8, 0.7));

        var report = CapacityModulationReport.from(defaults);
        var graduation = report.sites().stream()
                .filter(s -> "graduation-score".equals(s.site())).findFirst().orElseThrow();

        assertThat(graduation.capacityDimension()).isEqualTo("relationalComplexity");
        assertThat(graduation.capacityValue()).isEqualTo(0.3);
        assertThat(graduation.effect()).isEqualTo("multiplier");
    }

    @Test
    void strategyDeltaCeiling_usesWorkingMemorySpan() {
        var defaults = CognitiveDefaults.empty("agent-3")
                .withCapacityConfig(new CognitiveCapacityConfig(0.9, 0.8, 0.45));

        var report = CapacityModulationReport.from(defaults);
        var strategy = report.sites().stream()
                .filter(s -> "strategy-delta-ceiling".equals(s.site())).findFirst().orElseThrow();

        assertThat(strategy.capacityDimension()).isEqualTo("workingMemorySpan");
        assertThat(strategy.capacityValue()).isEqualTo(0.45);
        assertThat(strategy.effect()).isEqualTo("ceiling");
    }

    @Test
    void nullCapacityConfig_usesDefaults() {
        var defaults = CognitiveDefaults.empty("agent-4");

        var report = CapacityModulationReport.from(defaults);

        assertThat(report.sites()).hasSize(6);
        var capsAlpha = report.sites().stream()
                .filter(s -> "caps-alpha".equals(s.site())).findFirst().orElseThrow();
        assertThat(capsAlpha.capacityValue()).isEqualTo(1.0);
    }

    @Test
    void inspectService_delegatesToReport() {
        var defaults = CognitiveDefaults.empty("agent-5")
                .withCapacityConfig(new CognitiveCapacityConfig(0.5, 0.5, 0.5));

        var report = CognitionInspectService.inspectCapacity(defaults);

        assertThat(report.agentId()).isEqualTo("agent-5");
        assertThat(report.capacity().relationalComplexity()).isEqualTo(0.5);
    }
}

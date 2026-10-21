package io.casehub.neocortex.caps.engine;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.casehub.neocortex.caps.SituationActivation;
import io.casehub.neocortex.caps.TrajectoryEntry;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class HaikuBiasValidatorTest {

    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void parsesAuditEntries(@TempDir Path tempDir) throws IOException {
        var auditFile = tempDir.resolve("classification-audit.jsonl");
        var log = new ClassificationAuditLog(auditFile);
        log.write(10, "agent-a", "inline",
            List.of(new TrajectoryEntry(9, "something happened"), new TrajectoryEntry(10, "more happened")),
            List.of(new SituationActivation("reward", 0.8)),
            "Good things detected");

        var entries = HaikuBiasValidator.parseAuditFile(auditFile);
        assertThat(entries).hasSize(1);

        var entry = entries.getFirst();
        assertThat(entry.tick()).isEqualTo(10);
        assertThat(entry.agentId()).isEqualTo("agent-a");
        assertThat(entry.mode()).isEqualTo("inline");
        assertThat(entry.trajectory()).hasSize(2);
        assertThat(entry.activations()).hasSize(1);
        assertThat(entry.reasoning()).isEqualTo("Good things detected");
    }

    @Test
    void computesDivergence() {
        var inlineActivations = List.of(
            new SituationActivation("reward", 0.8),
            new SituationActivation("threat_perception", 0.6)
        );
        var haikuActivations = List.of(
            new SituationActivation("reward", 0.7),
            new SituationActivation("social_inclusion", 0.5)
        );

        var divergence = HaikuBiasValidator.computeDivergence(inlineActivations, haikuActivations);
        // Jaccard: intersection={reward}, union={reward, threat_perception, social_inclusion}
        // Jaccard similarity = 1/3, divergence = 1 - 1/3 = 2/3
        assertThat(divergence.jaccardDivergence()).isCloseTo(0.667, org.assertj.core.data.Offset.offset(0.01));
        assertThat(divergence.sharedNodes()).containsExactly("reward");
        assertThat(divergence.meanConfidenceDelta()).isGreaterThan(0.0);
    }

    @Test
    void perfectAgreementHasZeroDivergence() {
        var activations = List.of(
            new SituationActivation("reward", 0.8),
            new SituationActivation("threat_perception", 0.6)
        );

        var divergence = HaikuBiasValidator.computeDivergence(activations, activations);
        assertThat(divergence.jaccardDivergence()).isEqualTo(0.0);
        assertThat(divergence.meanConfidenceDelta()).isEqualTo(0.0);
    }

    @Test
    void emptyActivationsHandledGracefully() {
        var divergence = HaikuBiasValidator.computeDivergence(List.of(), List.of());
        assertThat(divergence.jaccardDivergence()).isEqualTo(0.0);
    }
}

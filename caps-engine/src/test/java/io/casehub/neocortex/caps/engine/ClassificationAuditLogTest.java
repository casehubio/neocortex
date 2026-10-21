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

class ClassificationAuditLogTest {

    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void writesOneJsonLinePerEntry(@TempDir Path tempDir) throws IOException {
        var logFile = tempDir.resolve("classification-audit.jsonl");
        var log = new ClassificationAuditLog(logFile);

        var trajectory = List.of(
            new TrajectoryEntry(46, "Hooded Claw entered"),
            new TrajectoryEntry(47, "You overheard whispering")
        );
        var activations = List.of(
            new SituationActivation("deception_detected", 0.7),
            new SituationActivation("threat_perception", 0.6)
        );
        log.write(47, "penelope-pitstop", "inline", trajectory,
                  activations, "Covert coordination detected");

        var lines = Files.readAllLines(logFile);
        assertThat(lines).hasSize(1);

        var node = mapper.readTree(lines.getFirst());
        assertThat(node.get("tick").asInt()).isEqualTo(47);
        assertThat(node.get("agentId").asText()).isEqualTo("penelope-pitstop");
        assertThat(node.get("mode").asText()).isEqualTo("inline");
        assertThat(node.get("trajectory")).hasSize(2);
        assertThat(node.get("activations")).hasSize(2);
        assertThat(node.get("reasoning").asText()).isEqualTo("Covert coordination detected");
    }

    @Test
    void writesSourceRangesWhenPresent(@TempDir Path tempDir) throws IOException {
        var logFile = tempDir.resolve("classification-audit.jsonl");
        var log     = new ClassificationAuditLog(logFile);

        var activations = List.of(
                new SituationActivation("deception_detected", 0.7, new int[]{120, 185}),
                new SituationActivation("threat_perception", 0.6)
                                 );
        log.write(47, "penelope-pitstop", "inline", List.of(),
                  activations, "Covert coordination detected");

        var node = mapper.readTree(Files.readAllLines(logFile).getFirst());
        var acts = node.get("activations");

        var first = acts.get(0);
        assertThat(first.get("node").asText()).isEqualTo("deception_detected");
        assertThat(first.has("source")).isTrue();
        assertThat(first.get("source").get(0).asInt()).isEqualTo(120);
        assertThat(first.get("source").get(1).asInt()).isEqualTo(185);

        var second = acts.get(1);
        assertThat(second.get("node").asText()).isEqualTo("threat_perception");
        assertThat(second.has("source")).isFalse();
    }


    @Test
    void appendsMultipleEntries(@TempDir Path tempDir) throws IOException {
        var logFile = tempDir.resolve("classification-audit.jsonl");
        var log = new ClassificationAuditLog(logFile);

        log.write(10, "agent-a", "inline", List.of(), List.of(), "");
        log.write(20, "agent-b", "external", List.of(), List.of(), "");

        var lines = Files.readAllLines(logFile);
        assertThat(lines).hasSize(2);
    }

    @Test
    void createsFileIfNotExists(@TempDir Path tempDir) throws IOException {
        var logFile = tempDir.resolve("subdir/classification-audit.jsonl");
        var log = new ClassificationAuditLog(logFile);
        log.write(1, "agent", "inline", List.of(), List.of(), "");
        assertThat(Files.exists(logFile)).isTrue();
    }
}

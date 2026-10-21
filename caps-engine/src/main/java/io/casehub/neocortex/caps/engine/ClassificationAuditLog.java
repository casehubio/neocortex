package io.casehub.neocortex.caps.engine;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.casehub.neocortex.caps.SituationActivation;
import io.casehub.neocortex.caps.TrajectoryEntry;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.List;

/**
 * Append-only JSONL writer for classification audit events.
 * One JSON line per classification, containing tick, agentId, mode,
 * trajectory, activations, and reasoning. This is the contract between
 * the scenario (producer) and the Haiku bias validator (consumer).
 */
public class ClassificationAuditLog {

    private final Path logFile;
    private final ObjectMapper mapper = new ObjectMapper();

    public ClassificationAuditLog(Path logFile) {
        this.logFile = logFile;
    }

    public void write(int tick, String agentId, String mode,
                      List<TrajectoryEntry> trajectory,
                      List<SituationActivation> activations,
                      String reasoning) throws IOException {
        ObjectNode root = mapper.createObjectNode();
        root.put("tick", tick);
        root.put("agentId", agentId);
        root.put("mode", mode);

        ArrayNode trajArray = root.putArray("trajectory");
        for (var entry : trajectory) {
            ObjectNode e = trajArray.addObject();
            e.put("tick", entry.tick());
            e.put("text", entry.actionResult());
        }

        ArrayNode actArray = root.putArray("activations");
        for (var act : activations) {
            ObjectNode a = actArray.addObject();
            a.put("node", act.nodeId());
            a.put("confidence", act.confidence());
            if (act.source() != null) {
                ArrayNode sourceArray = a.putArray("source");
                for (int offset : act.source()) {
                    sourceArray.add(offset);
                }
            }
        }

        root.put("reasoning", reasoning);

        Files.createDirectories(logFile.getParent());
        Files.writeString(logFile, mapper.writeValueAsString(root) + "\n",
            StandardOpenOption.CREATE, StandardOpenOption.APPEND);
    }
}

package io.quarkmind.inference.bayes;

import java.util.Map;

public record NetworkLink(
    String sourceSlot,
    String targetSlot,
    Map<String, String> variableMapping,
    TransferMode transferMode
) {
    public NetworkLink {
        if (sourceSlot == null || targetSlot == null) {
            throw new IllegalArgumentException("Source and target slot must not be null");
        }
        if (sourceSlot.equals(targetSlot)) {
            throw new IllegalArgumentException("Self-links are not allowed: " + sourceSlot);
        }
        if (variableMapping == null || variableMapping.isEmpty()) {
            throw new IllegalArgumentException("Variable mapping must not be null or empty");
        }
        variableMapping = Map.copyOf(variableMapping);
        if (transferMode == null) {
            transferMode = TransferMode.POSTERIOR;
        }
    }
}

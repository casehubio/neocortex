package io.casehub.neocortex.memory.cbr;

import io.casehub.neocortex.memory.Memory;
import io.casehub.neocortex.memory.MemoryAttributeKeys;
import io.casehub.neocortex.memory.MemoryDomain;
import io.casehub.neocortex.memory.MemoryInput;
import io.casehub.neocortex.memory.Subject;

import java.util.HashMap;
import java.util.Objects;

public record CbrCaseEntry(
        String problem,
        String solution,
        String outcome,
        double confidence
) {
    public static final MemoryDomain DOMAIN = new MemoryDomain("solution");

    public CbrCaseEntry {
        Objects.requireNonNull(problem, "problem must not be null");
        if (problem.isBlank()) throw new IllegalArgumentException("problem must not be blank");
        if (confidence < 0 || confidence > 1)
            throw new IllegalArgumentException("confidence must be in [0,1], got: " + confidence);
    }

    public MemoryInput toMemoryInput(Subject subject, String tenantId) {
        var attrs = new HashMap<String, String>();
        if (solution != null) attrs.put(MemoryAttributeKeys.SOLUTION, solution);
        if (outcome != null) attrs.put(MemoryAttributeKeys.OUTCOME, outcome);
        attrs.put(MemoryAttributeKeys.CONFIDENCE, MemoryAttributeKeys.formatConfidence(confidence));
        return new MemoryInput(subject, DOMAIN, tenantId, null, problem,
                attrs, null, null, null, null, null, null);
    }

    public static CbrCaseEntry from(Memory memory) {
        Objects.requireNonNull(memory, "memory must not be null");
        String confStr = memory.attributes().get(MemoryAttributeKeys.CONFIDENCE);
        double conf = confStr != null ? MemoryAttributeKeys.parseConfidence(confStr) : 0.0;
        return new CbrCaseEntry(
                memory.text(),
                memory.attributes().get(MemoryAttributeKeys.SOLUTION),
                memory.attributes().get(MemoryAttributeKeys.OUTCOME),
                conf);
    }
}

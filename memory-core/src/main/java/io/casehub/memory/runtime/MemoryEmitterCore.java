package io.casehub.memory.runtime;

import io.casehub.neocortex.memory.CaseMemoryStore;
import io.casehub.neocortex.memory.MemoryInput;

import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

public class MemoryEmitterCore {

    private static final Logger LOG = Logger.getLogger(MemoryEmitterCore.class.getName());

    private final CaseMemoryStore store;

    public MemoryEmitterCore(CaseMemoryStore store) {
        this.store = store;
    }

    public void emit(MemoryInput input) {
        try {
            store.store(input);
        } catch (SecurityException e) {
            throw e;
        } catch (Exception e) {
            LOG.log(Level.WARNING, "Memory emission failed for entity=" +
                input.subject().id() + " domain=" + input.domain().name() +
                " tenant=" + input.tenantId(), e);
        }
    }

    public void emitAll(List<MemoryInput> inputs) {
        if (inputs.isEmpty()) return;
        try {
            var result = store.storeAll(inputs);
            if (!result.allSucceeded()) {
                LOG.warning("Memory batch partial failure: " + result.failures().size() +
                    "/" + inputs.size() + " inputs failed (first entity=" +
                    inputs.getFirst().subject().id() + " domain=" +
                    inputs.getFirst().domain().name() + ")");
            }
        } catch (SecurityException e) {
            throw e;
        } catch (Exception e) {
            LOG.log(Level.WARNING, "Memory batch emission failed (" + inputs.size() +
                " inputs, first entity=" + inputs.getFirst().subject().id() +
                " domain=" + inputs.getFirst().domain().name() + ")", e);
        }
    }
}

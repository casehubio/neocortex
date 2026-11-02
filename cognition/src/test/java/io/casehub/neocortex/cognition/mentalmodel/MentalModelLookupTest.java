package io.casehub.neocortex.cognition.mentalmodel;

import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class MentalModelLookupTest {

    @Test
    void noOpLookupReturnsEmpty() {
        MentalModelLookup noOp = (agentId, subjectId, tenantId) -> Optional.empty();
        assertThat(noOp.lookup("agent-1", "subject-1", "tenant-1")).isEmpty();
    }

    @Test
    void mentalModelMemoryImplementsLookup() {
        assertThat(MentalModelLookup.class.isAssignableFrom(MentalModelMemory.class)).isTrue();
    }
}

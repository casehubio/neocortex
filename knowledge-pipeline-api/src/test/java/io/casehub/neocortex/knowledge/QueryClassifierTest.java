package io.casehub.neocortex.knowledge;

import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class QueryClassifierTest {

    private final QueryClassifier noOp = (nl, domain) -> Optional.empty();

    @Test
    void noOpReturnsEmpty() {
        assertThat(noOp.classify("find pizza", KnowledgeDomain.PLACE)).isEmpty();
    }

    @Test
    void noOpReturnsEmptyForBlank() {
        assertThat(noOp.classify("", KnowledgeDomain.PLACE)).isEmpty();
    }
}

package io.casehub.neocortex.rag;

import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ProvenanceRecordTest {

    @Test
    void validRecord_allFieldsAccessible() {
        var now = Instant.now();
        var record = new ProvenanceRecord("r1", "ctx", "action1", "issue", "doc1", "agent", now);
        assertThat(record.id()).isEqualTo("r1");
        assertThat(record.retrievalContext()).isEqualTo("ctx");
        assertThat(record.actionId()).isEqualTo("action1");
        assertThat(record.actionType()).isEqualTo("issue");
        assertThat(record.documentId()).isEqualTo("doc1");
        assertThat(record.recordedBy()).isEqualTo("agent");
        assertThat(record.timestamp()).isEqualTo(now);
    }

    @Test
    void nullId_rejected() {
        assertThatThrownBy(() -> new ProvenanceRecord(null, "ctx", "a", "t", "d", "r", Instant.now()))
            .isInstanceOf(NullPointerException.class);
    }

    @Test
    void nullActionId_rejected() {
        assertThatThrownBy(() -> new ProvenanceRecord("r1", "ctx", null, "t", "d", "r", Instant.now()))
            .isInstanceOf(NullPointerException.class);
    }

    @Test
    void nullDocumentId_rejected() {
        assertThatThrownBy(() -> new ProvenanceRecord("r1", "ctx", "a", "t", null, "r", Instant.now()))
            .isInstanceOf(NullPointerException.class);
    }

    @Test
    void nullTimestamp_rejected() {
        assertThatThrownBy(() -> new ProvenanceRecord("r1", "ctx", "a", "t", "d", "r", null))
            .isInstanceOf(NullPointerException.class);
    }

    @Test
    void nullRetrievalContext_allowed() {
        var record = new ProvenanceRecord("r1", null, "a", "t", "d", "r", Instant.now());
        assertThat(record.retrievalContext()).isNull();
    }

    @Test
    void nullRecordedBy_allowed() {
        var record = new ProvenanceRecord("r1", "ctx", "a", "t", "d", null, Instant.now());
        assertThat(record.recordedBy()).isNull();
    }

    @Test
    void provenanceStats_empty() {
        assertThat(ProvenanceStats.EMPTY.totalRecords()).isZero();
        assertThat(ProvenanceStats.EMPTY.topReferenced()).isEmpty();
    }

    @Test
    void provenanceStats_topReferencedImmutable() {
        var stats = new ProvenanceStats(5, 3, 2,
            java.util.List.of(new ProvenanceStats.DocumentRefCount("d1", 3)), 1);
        assertThatThrownBy(() -> stats.topReferenced().add(new ProvenanceStats.DocumentRefCount("d2", 1)))
            .isInstanceOf(UnsupportedOperationException.class);
    }
}

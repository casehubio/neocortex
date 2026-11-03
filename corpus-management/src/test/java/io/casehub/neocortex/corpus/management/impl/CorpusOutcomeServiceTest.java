package io.casehub.neocortex.corpus.management.impl;

import io.casehub.neocortex.memory.cbr.CbrRecordStore;
import io.casehub.neocortex.memory.cbr.inmem.InMemoryCbrRecordStore;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class CorpusOutcomeServiceTest {

    private CbrRecordStore cbrStore;
    private CorpusOutcomeService service;

    @BeforeEach
    void setUp() {
        cbrStore = new InMemoryCbrRecordStore();
        service = new CorpusOutcomeService(cbrStore, "test-corpus", "corpus", "corpus-outcome");
    }

    @Test
    void recordOutcomeCreatesCase() {
        String result = service.recordOutcome(
                "CE-20261102-a1b2c3", "casehubio/neocortex", 775,
                "Refactoring corpus management", 0.8, "Relevant and current");

        assertThat(result).contains("CE-20261102-a1b2c3");
        assertThat(result).contains("recorded");
    }

    @Test
    void secondOutcomeAdjustsConfidence() {
        service.recordOutcome("CE-20261102-a1b2c3", "casehubio/neocortex", 775,
                "First task", 1.0, "Very helpful");
        service.recordOutcome("CE-20261102-a1b2c3", "casehubio/neocortex", 776,
                "Second task", 0.0, "Not relevant");

        String report = service.outcomeReport();
        assertThat(report).contains("CE-20261102-a1b2c3");
        assertThat(report).contains("trust");
    }

    @Test
    void outcomeReportEmptyWhenNoOutcomes() {
        String report = service.outcomeReport();
        assertThat(report).contains("No outcome data");
    }

    @Test
    void recordOutcomeDoesNotCreateDuplicates() {
        service.recordOutcome("CE-20261102-a1b2c3", "casehubio/neocortex", 775,
                "First", 1.0, null);
        service.recordOutcome("CE-20261102-a1b2c3", "casehubio/neocortex", 776,
                "Second", 0.5, null);
        service.recordOutcome("CE-20261102-a1b2c3", "casehubio/neocortex", 777,
                "Third", 0.0, null);

        String report = service.outcomeReport();
        long count = report.lines()
                .filter(l -> l.contains("CE-20261102-a1b2c3"))
                .count();
        assertThat(count).isEqualTo(1);
    }
}

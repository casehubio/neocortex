package io.casehub.neocortex.corpus.management.impl;

import io.casehub.neocortex.cognitive.Confidence;
import io.casehub.neocortex.memory.MemoryDomain;
import io.casehub.neocortex.memory.cbr.CbrGuidanceRecord;
import io.casehub.neocortex.memory.cbr.CbrOutcome;
import io.casehub.neocortex.memory.cbr.CbrRecordStore;
import io.casehub.neocortex.memory.cbr.CbrRecordSummary;
import io.casehub.neocortex.memory.cbr.CbrScanRequest;
import io.casehub.neocortex.memory.cbr.CbrScanResult;
import io.casehub.platform.api.path.Path;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public class CorpusOutcomeService {

    private final CbrRecordStore cbrStore;
    private final String tenantId;
    private final String scopePrefix;
    private final String caseType;

    public CorpusOutcomeService(CbrRecordStore cbrStore, String tenantId,
                                 String scopePrefix, String caseType) {
        this.cbrStore = cbrStore;
        this.tenantId = tenantId;
        this.scopePrefix = scopePrefix;
        this.caseType = caseType;
    }

    public String recordOutcome(String entryId, String issueRepo, int issueNumber,
                                 String workContext, double successRate, String detail) {
        boolean exists = scanAllCases().stream()
                .anyMatch(c -> entryId.equals(c.caseId()));

        if (!exists) {
            String problem = workContext + " (" + issueRepo + "#" + issueNumber + ")";
            var cbrCase = new CbrGuidanceRecord(problem, entryId, null,
                    Confidence.unknown(1.0), null, null);
            cbrStore.store(cbrCase, caseType, entryId,
                    new MemoryDomain("knowledge"), tenantId, entryId,
                    Path.of(scopePrefix, tenantId));
        }

        CbrOutcome outcome = CbrOutcome.of(successRate,
                detail != null ? detail : "", Instant.now());
        cbrStore.recordOutcome(entryId, outcome, tenantId);

        return "Outcome recorded for " + entryId + " (success=" + successRate + ")";
    }

    public String outcomeReport() {
        List<CbrRecordSummary> cases = scanAllCases();

        if (cases.isEmpty()) {
            return "No outcome data recorded yet.";
        }

        List<CbrRecordSummary> sorted = cases.stream()
                .sorted(Comparator.comparingDouble(c -> c.trustScore() != null ? c.trustScore() : 1.0))
                .toList();

        StringBuilder sb = new StringBuilder();
        sb.append("## Corpus Entry Outcome Report\n\n");
        sb.append("Entries with recorded outcomes, sorted by trust score (lowest first):\n\n");

        for (CbrRecordSummary c : sorted) {
            sb.append("- **").append(c.caseId()).append("**");
            if (c.trustScore() != null) {
                sb.append(" — trust: ").append(String.format("%.2f", c.trustScore()));
            }
            sb.append("\n");
        }

        return sb.toString();
    }

    public List<OutcomeEntry> outcomeEntries() {
        return scanAllCases().stream()
                .sorted(Comparator.comparingDouble(c -> c.trustScore() != null ? c.trustScore() : 1.0))
                .map(c -> new OutcomeEntry(c.caseId(), c.trustScore()))
                .toList();
    }

    public void clearAll() {
        cbrStore.eraseByScope(Path.of(scopePrefix, tenantId), tenantId);
    }

    private List<CbrRecordSummary> scanAllCases() {
        List<CbrRecordSummary> all = new ArrayList<>();
        String cursor = null;
        do {
            CbrScanResult page = cbrStore.scan(new CbrScanRequest(
                    tenantId, new MemoryDomain("knowledge"), caseType, 100, cursor));
            all.addAll(page.items());
            cursor = page.hasMore() ? page.nextCursor() : null;
        } while (cursor != null);
        return all;
    }
}

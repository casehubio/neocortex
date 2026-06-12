package io.casehub.corpus;

public interface CorpusIntegrity {
    IntegrityReport check();
    IntegrityReport checkAndRecover();
    IntegrityReport fullHashVerification();
}

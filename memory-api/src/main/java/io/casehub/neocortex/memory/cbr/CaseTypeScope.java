package io.casehub.neocortex.memory.cbr;

import java.util.Objects;

public sealed interface CaseTypeScope {
    record Specific(String caseType) implements CaseTypeScope {
        public Specific {
            Objects.requireNonNull(caseType, "caseType required");
        }
    }

    record AllInDomain() implements CaseTypeScope {}
}

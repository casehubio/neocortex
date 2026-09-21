package io.casehub.neocortex.rag;

import java.util.Map;

@FunctionalInterface
public interface QueryExtractionStrategy {
    RetrievalQuery extractQuery(Map<String, Object> caseContext);
}

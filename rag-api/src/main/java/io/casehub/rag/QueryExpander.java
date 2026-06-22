package io.casehub.rag;

public interface QueryExpander {
    RetrievalQuery expand(RetrievalQuery query);
}

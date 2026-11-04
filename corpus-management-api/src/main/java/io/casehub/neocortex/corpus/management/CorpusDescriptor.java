package io.casehub.neocortex.corpus.management;

import io.casehub.neocortex.rag.CorpusRef;

import java.time.Instant;
import java.util.List;
import java.util.Map;

public record CorpusDescriptor(
    String id,
    String name,
    CorpusType type,
    CorpusLifecycle lifecycle,
    CorpusRef corpusRef,
    String vectorCollection,
    List<FederationRef> upstream,
    List<FederationRef> peers,
    Map<String, String> metadata,
    Instant created,
    Instant lastModified
) {}

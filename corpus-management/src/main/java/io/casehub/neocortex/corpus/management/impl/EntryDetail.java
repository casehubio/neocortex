package io.casehub.neocortex.corpus.management.impl;

import java.util.List;

public record EntryDetail(
        String id, String title, String domain, String type,
        double score, String body, String source, String sourcePrefix,
        List<String> seeAlsoIds) {}

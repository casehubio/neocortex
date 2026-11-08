package io.casehub.neocortex.corpus.management.impl;

import java.util.List;

public record FindingInput(String topic, String insight, String sourceIssue,
                           List<String> tags, String domain, String type) {}

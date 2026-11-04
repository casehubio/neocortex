package io.casehub.neocortex.corpus.management.impl;

public record FeedbackContext(String documentId, String issueRepo, int issueNumber,
                              String outcome, String recordedAt) {}

package io.casehub.neocortex.corpus.management.impl;

public record FeedbackContext(String geId, String issueRepo, int issueNumber,
                              String outcome, String recordedAt) {}

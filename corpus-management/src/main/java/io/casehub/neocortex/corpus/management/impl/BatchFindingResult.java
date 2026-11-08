package io.casehub.neocortex.corpus.management.impl;

import java.util.List;

public record BatchFindingResult(List<String> entryIds, int total, int succeeded, List<String> errors) {}

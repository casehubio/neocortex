package io.casehub.neocortex.corpus.management.impl;

import java.util.List;

public record ImageProcessingResult(List<CapturedImage> images) {

    public static final ImageProcessingResult EMPTY = new ImageProcessingResult(List.of());

    public ImageProcessingResult {
        images = images == null ? List.of() : List.copyOf(images);
    }
}

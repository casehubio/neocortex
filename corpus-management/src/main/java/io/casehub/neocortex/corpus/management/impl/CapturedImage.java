package io.casehub.neocortex.corpus.management.impl;

public record CapturedImage(
    String sourceUrl,
    String altText,
    byte[] imageBytes,
    String mimeType,
    String description
) {
    public CapturedImage {
        if (sourceUrl == null) throw new IllegalArgumentException("sourceUrl must not be null");
        if (imageBytes == null) throw new IllegalArgumentException("imageBytes must not be null");
        if (altText == null) altText = "";
        if (mimeType == null) mimeType = "image/png";
    }
}

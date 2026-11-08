package io.casehub.neocortex.corpus.management;

@FunctionalInterface
public interface ImageDescriber {
    String describe(byte[] imageBytes, String mimeType);
}

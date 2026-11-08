package io.casehub.neocortex.corpus.management;

@FunctionalInterface
public interface LayoutClassifier {
    LayoutClassification classify(byte[] pageImage, String mimeType);
}

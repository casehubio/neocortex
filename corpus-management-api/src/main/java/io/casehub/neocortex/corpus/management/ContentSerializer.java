package io.casehub.neocortex.corpus.management;

@FunctionalInterface
public interface ContentSerializer {
    byte[] serialize(CorpusEntry entry);
}

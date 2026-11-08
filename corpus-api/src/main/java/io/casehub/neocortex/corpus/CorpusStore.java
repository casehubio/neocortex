package io.casehub.neocortex.corpus;

import java.io.InputStream;
import java.nio.file.Path;

public interface CorpusStore {
    void write(String path, byte[] content);
    void write(String path, InputStream content);
    void write(String path, Path file);
    void delete(String path);

    void flush();

}

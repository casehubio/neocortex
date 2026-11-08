package io.casehub.neocortex.corpus;

import java.io.InputStream;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Consumer;

public class AccessTrackingReader implements CorpusReader {

    private final CorpusReader delegate;
    private final String corpusName;
    private final Consumer<AccessEvent> eventSink;

    public AccessTrackingReader(CorpusReader delegate, String corpusName,
                                Consumer<AccessEvent> eventSink) {
        this.delegate = Objects.requireNonNull(delegate);
        this.corpusName = Objects.requireNonNull(corpusName);
        this.eventSink = Objects.requireNonNull(eventSink);
    }

    @Override
    public Optional<byte[]> read(String path) {
        Optional<byte[]> result = delegate.read(path);
        if (result.isPresent()) {
            eventSink.accept(new AccessEvent(path, corpusName, Instant.now()));
        }
        return result;
    }

    @Override
    public Optional<InputStream> readStream(String path) {
        return delegate.readStream(path);
    }

    @Override
    public Optional<byte[]> readVersion(String path, int version) {
        return delegate.readVersion(path, version);
    }

    @Override
    public List<VersionInfo> versions(String path) {
        return delegate.versions(path);
    }

    @Override
    public List<String> list() {
        return delegate.list();
    }

    @Override
    public List<String> list(String prefix) {
        return delegate.list(prefix);
    }

    @Override
    public boolean exists(String path) {
        return delegate.exists(path);
    }
}

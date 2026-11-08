package io.casehub.neocortex.corpus.git;

import io.casehub.neocortex.corpus.CorpusStore;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.lib.Repository;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Set;

public class GitCorpusStore implements CorpusStore {

    private final Repository repository;
    private final Set<String> pendingPaths = new LinkedHashSet<>();

    public GitCorpusStore(Repository repository) {
        this.repository = Objects.requireNonNull(repository);
    }

    @Override
    public void write(String path, byte[] content) {
        Objects.requireNonNull(path);
        Objects.requireNonNull(content);
        try {
            Path target = repository.getWorkTree().toPath().resolve(path);
            Files.createDirectories(target.getParent());
            Files.write(target, content);
            pendingPaths.add(path);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    @Override
    public void write(String path, InputStream content) {
        try {
            write(path, content.readAllBytes());
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    @Override
    public void write(String path, Path file) {
        try {
            write(path, Files.readAllBytes(file));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    @Override
    public void delete(String path) {
        try (Git git = new Git(repository)) {
            git.rm().addFilepattern(path).call();
            pendingPaths.add(path);
        } catch (GitAPIException e) {
            throw new RuntimeException("Failed to git rm " + path, e);
        }
    }

    @Override
    public void flush() {
        if (pendingPaths.isEmpty()) return;
        try (Git git = new Git(repository)) {
            for (String path : pendingPaths) {
                Path target = repository.getWorkTree().toPath().resolve(path);
                if (Files.exists(target)) {
                    git.add().addFilepattern(path).call();
                }
            }
            git.commit().setMessage("corpus: write " + pendingPaths.size() + " file(s)").call();
            pendingPaths.clear();
        } catch (GitAPIException e) {
            throw new RuntimeException("Failed to flush corpus writes", e);
        }
    }
}

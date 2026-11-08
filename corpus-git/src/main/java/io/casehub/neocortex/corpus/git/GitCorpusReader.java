package io.casehub.neocortex.corpus.git;

import io.casehub.neocortex.corpus.CorpusReader;
import io.casehub.neocortex.corpus.VersionInfo;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.ObjectLoader;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.treewalk.TreeWalk;
import org.eclipse.jgit.treewalk.filter.PathFilter;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

public class GitCorpusReader implements CorpusReader {

    private final Repository repository;
    private final String refSpec;

    public GitCorpusReader(Repository repository, String refSpec) {
        this.repository = Objects.requireNonNull(repository);
        this.refSpec = Objects.requireNonNull(refSpec);
    }

    @Override
    public Optional<byte[]> read(String path) {
        try {
            ObjectId commitId = repository.resolve(refSpec);
            if (commitId == null) return Optional.empty();
            try (RevWalk revWalk = new RevWalk(repository)) {
                RevCommit commit = revWalk.parseCommit(commitId);
                try (TreeWalk treeWalk = TreeWalk.forPath(repository, path, commit.getTree())) {
                    if (treeWalk == null) return Optional.empty();
                    ObjectLoader loader = repository.open(treeWalk.getObjectId(0));
                    return Optional.of(loader.getBytes());
                }
            }
        } catch (IOException e) {
            return Optional.empty();
        }
    }

    @Override
    public Optional<InputStream> readStream(String path) {
        return read(path).map(ByteArrayInputStream::new);
    }

    @Override
    public Optional<byte[]> readVersion(String path, int version) {
        List<VersionInfo> history = versions(path);
        if (version < 1 || version > history.size()) return Optional.empty();
        VersionInfo vi = history.get(version - 1);
        try {
            ObjectId commitId = ObjectId.fromString(vi.source());
            try (RevWalk revWalk = new RevWalk(repository)) {
                RevCommit commit = revWalk.parseCommit(commitId);
                try (TreeWalk treeWalk = TreeWalk.forPath(repository, path, commit.getTree())) {
                    if (treeWalk == null) return Optional.empty();
                    ObjectLoader loader = repository.open(treeWalk.getObjectId(0));
                    return Optional.of(loader.getBytes());
                }
            }
        } catch (IOException e) {
            return Optional.empty();
        }
    }

    @Override
    public List<VersionInfo> versions(String path) {
        try {
            ObjectId head = repository.resolve(refSpec);
            if (head == null) {return List.of();}
            try (RevWalk revWalk = new RevWalk(repository)) {
                revWalk.markStart(revWalk.parseCommit(head));
                revWalk.setTreeFilter(PathFilter.create(path));
                List<RevCommit> commits = new ArrayList<>();
                for (RevCommit commit : revWalk) {
                    commits.add(commit);
                }
                Collections.reverse(commits);
                List<VersionInfo> versions = new ArrayList<>(commits.size());
                for (int i = 0; i < commits.size(); i++) {
                    RevCommit c = commits.get(i);
                    versions.add(new VersionInfo(i + 1, c.getAuthorIdent().getWhenAsInstant(),
                                                 c.getId().getName(), c.getAuthorIdent().getName()));
                }
                return versions;
            }
        } catch (IOException e) {
            return List.of();
        }
    }

    @Override
    public List<String> list() {
        return list("");
    }

    @Override
    public List<String> list(String prefix) {
        try {
            ObjectId head = repository.resolve(refSpec);
            if (head == null) return List.of();
            try (RevWalk revWalk = new RevWalk(repository)) {
                RevCommit commit = revWalk.parseCommit(head);
                try (TreeWalk treeWalk = new TreeWalk(repository)) {
                    treeWalk.addTree(commit.getTree());
                    treeWalk.setRecursive(true);
                    List<String> paths = new ArrayList<>();
                    while (treeWalk.next()) {
                        String p = treeWalk.getPathString();
                        String fileName = p.contains("/") ? p.substring(p.lastIndexOf('/') + 1) : p;
                        if (fileName.startsWith(".") || fileName.startsWith("_")) continue;
                        if (prefix.isEmpty() || p.startsWith(prefix)) {
                            paths.add(p);
                        }
                    }
                    return paths;
                }
            }
        } catch (IOException e) {
            return List.of();
        }
    }

    @Override
    public boolean exists(String path) {
        return read(path).isPresent();
    }
}

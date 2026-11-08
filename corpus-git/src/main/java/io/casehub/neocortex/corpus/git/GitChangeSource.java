package io.casehub.neocortex.corpus.git;

import io.casehub.neocortex.corpus.ChangeListener;
import io.casehub.neocortex.corpus.ChangeSet;
import io.casehub.neocortex.corpus.ChangeType;
import io.casehub.neocortex.corpus.ChangedEntry;
import io.casehub.neocortex.corpus.WatchableChangeSource;
import org.eclipse.jgit.diff.DiffEntry;
import org.eclipse.jgit.diff.DiffFormatter;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.treewalk.TreeWalk;
import org.eclipse.jgit.util.io.DisabledOutputStream;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public class GitChangeSource implements WatchableChangeSource {

    private final Repository repository;
    private final String refSpec;

    public GitChangeSource(Repository repository, String refSpec) {
        this.repository = Objects.requireNonNull(repository);
        this.refSpec = Objects.requireNonNull(refSpec);
    }

    @Override
    public ChangeSet changesSince(String cursor) {
        try {
            ObjectId currentId = repository.resolve(refSpec);
            if (currentId == null) return new ChangeSet(List.of(), cursor);
            String newCursor = currentId.getName();
            if (cursor != null && cursor.equals(newCursor)) {
                return new ChangeSet(List.of(), newCursor);
            }

            try (RevWalk revWalk = new RevWalk(repository)) {
                RevCommit newCommit = revWalk.parseCommit(currentId);

                if (cursor == null) {
                    return fullScanFromTree(newCommit, newCursor);
                }

                ObjectId oldId = ObjectId.fromString(cursor);
                RevCommit oldCommit = revWalk.parseCommit(oldId);

                List<ChangedEntry> entries = diffTrees(oldCommit, newCommit);
                return new ChangeSet(entries, newCursor);
            }
        } catch (IOException e) {
            return new ChangeSet(List.of(), cursor);
        }
    }

    @Override
    public ChangeSet fullScan() {
        try {
            ObjectId head = repository.resolve(refSpec);
            if (head == null) return new ChangeSet(List.of(), null);
            try (RevWalk revWalk = new RevWalk(repository)) {
                RevCommit commit = revWalk.parseCommit(head);
                return fullScanFromTree(commit, head.getName());
            }
        } catch (IOException e) {
            return new ChangeSet(List.of(), null);
        }
    }

    @Override
    public String currentCursor() {
        try {
            ObjectId head = repository.resolve(refSpec);
            return head != null ? head.getName() : null;
        } catch (IOException e) {
            return null;
        }
    }

    @Override
    public void watch(ChangeListener listener) {
        // Poll-based: caller invokes changesSince() periodically
    }

    @Override
    public void close() {
        // Repository lifecycle managed by caller
    }

    private ChangeSet fullScanFromTree(RevCommit commit, String cursor) throws IOException {
        try (TreeWalk treeWalk = new TreeWalk(repository)) {
            treeWalk.addTree(commit.getTree());
            treeWalk.setRecursive(true);
            List<ChangedEntry> entries = new ArrayList<>();
            while (treeWalk.next()) {
                entries.add(new ChangedEntry(treeWalk.getPathString(), ChangeType.ADDED));
            }
            return new ChangeSet(entries, cursor);
        }
    }

    private List<ChangedEntry> diffTrees(RevCommit oldCommit, RevCommit newCommit) throws IOException {
        try (DiffFormatter df = new DiffFormatter(DisabledOutputStream.INSTANCE)) {
            df.setRepository(repository);
            List<DiffEntry> diffs = df.scan(oldCommit.getTree(), newCommit.getTree());
            List<ChangedEntry> entries = new ArrayList<>(diffs.size());
            for (DiffEntry diff : diffs) {
                ChangeType type = switch (diff.getChangeType()) {
                    case ADD -> ChangeType.ADDED;
                    case DELETE -> ChangeType.DELETED;
                    case MODIFY, COPY, RENAME -> ChangeType.MODIFIED;
                };
                String path = diff.getChangeType() == DiffEntry.ChangeType.DELETE
                        ? diff.getOldPath() : diff.getNewPath();
                entries.add(new ChangedEntry(path, type));
            }
            return entries;
        }
    }
}

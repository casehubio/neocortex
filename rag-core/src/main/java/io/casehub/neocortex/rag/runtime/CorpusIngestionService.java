package io.casehub.neocortex.rag.runtime;

import io.casehub.neocortex.corpus.ChangeSet;
import io.casehub.neocortex.corpus.ChangedEntry;
import io.casehub.neocortex.corpus.WatchableChangeSource;
import io.casehub.neocortex.rag.CorpusRef;
import io.casehub.neocortex.rag.CursorStore;
import io.casehub.neocortex.rag.EmbeddingIngestor;
import io.casehub.neocortex.rag.TextChunker;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;
import java.util.logging.Level;
import java.util.logging.Logger;

public class CorpusIngestionService {

    private static final Logger LOG = Logger.getLogger(CorpusIngestionService.class.getName());

    private final EmbeddingIngestor ingestor;
    private final IngestionPipeline pipeline;

    private final CursorStore                              cursorStore;
    private final ConcurrentHashMap<String, ReentrantLock> locks          = new ConcurrentHashMap<>();
    private final List<WatchableChangeSource>              activeWatchers = new ArrayList<>();

    public CorpusIngestionService(EmbeddingIngestor ingestor, CursorStore cursorStore) {
        this.ingestor    = ingestor;
        this.cursorStore = cursorStore;
        this.pipeline    = new IngestionPipeline(ingestor);
    }

    static IngestionMode modeFor(CorpusIngestionBinding binding, IngestionConfig config) {
        if (config == null) {return IngestionMode.AUTO;}
        var corpusConfig = config.corpora().get(binding.name());
        if (corpusConfig == null) {return IngestionMode.AUTO;}
        return corpusConfig.mode();
    }

    static TextChunker splitterFor(String corpusName, IngestionConfig config) {
        if (config == null) {return null;}
        var corpusConfig = config.corpora().get(corpusName);
        if (corpusConfig == null || "none".equalsIgnoreCase(corpusConfig.chunking())) {
            return null;
        }
        if ("recursive".equalsIgnoreCase(corpusConfig.chunking())) {
            int maxSize = corpusConfig.chunkingMaxSize().orElse(1000);
            int overlap = corpusConfig.chunkingOverlapSize().orElse(200);
            return TextChunker.recursive(maxSize, overlap);
        }
        return null;
    }

    public List<WatchableChangeSource> activeWatchers() {
        return activeWatchers;
    }

    public void startAutoBindings(Iterable<CorpusIngestionBinding> bindings,
                                  IngestionConfig config) {
        for (CorpusIngestionBinding binding : bindings) {
            IngestionMode mode = modeFor(binding, config);
            if (mode != IngestionMode.AUTO) {continue;}

            processBinding(binding, splitterFor(binding.name(), config));

            if (binding.changeSource() instanceof WatchableChangeSource watchable) {
                try {
                    watchable.watch(entries -> onWatchEvent(binding, entries, config));
                    activeWatchers.add(watchable);
                    LOG.info(() -> "Started filesystem watcher for corpus '" + binding.name() + "'");
                } catch (Exception e) {
                    LOG.log(Level.WARNING,
                            "Failed to start watcher for corpus '" + binding.name()
                            + "' — falling back to polling", e);
                }
            }
        }
    }

    public void shutdown() {
        for (WatchableChangeSource watchable : activeWatchers) {
            try {
                watchable.close();
            } catch (Exception e) {
                LOG.log(Level.WARNING, "Failed to close watcher", e);
            }
        }
        activeWatchers.clear();
    }

    private void onWatchEvent(CorpusIngestionBinding binding, List<ChangedEntry> entries,
                              IngestionConfig config) {
        if (entries.isEmpty()) {return;}

        ReentrantLock lock = locks.computeIfAbsent(binding.name(), k -> new ReentrantLock());
        if (!lock.tryLock()) {
            LOG.fine(() -> "Skipping watch event for corpus '"
                           + binding.name() + "' — already being processed");
            return;
        }
        try {
            doProcessWatchEvent(binding, entries, splitterFor(binding.name(), config));
        } finally {
            lock.unlock();
        }
    }

    private void doProcessWatchEvent(CorpusIngestionBinding binding,
                                     List<ChangedEntry> entries,
                                     TextChunker splitter) {
        IngestionContext context = new IngestionContext(
                binding.corpusRef(), binding.metadataExtractor(), splitter);
        boolean anyFailure = false;

        for (ChangedEntry entry : entries) {
            try {
                switch (entry.type()) {
                    case DELETED -> pipeline.delete(entry.path(), binding.corpusRef());
                    case ADDED -> pipeline.ingest(entry.path(), binding.corpusReader(), context);
                    case MODIFIED -> {
                        pipeline.delete(entry.path(), binding.corpusRef());
                        pipeline.ingest(entry.path(), binding.corpusReader(), context);
                    }
                }
            } catch (Exception e) {
                LOG.log(Level.WARNING, "Failed to process document '" + entry.path() + "'", e);
                anyFailure = true;
            }
        }

        if (!anyFailure && binding.changeSource() instanceof WatchableChangeSource watchable) {
            cursorStore.save(binding.name(), watchable.currentCursor());
        }
    }

    public void processBinding(CorpusIngestionBinding binding) {
        processBinding(binding, null);
    }

    public void processBinding(CorpusIngestionBinding binding, TextChunker splitter) {
        ReentrantLock lock = locks.computeIfAbsent(binding.name(), k -> new ReentrantLock());
        if (!lock.tryLock()) {
            LOG.fine(() -> "Skipping corpus '" + binding.name() + "' — already being processed");
            return;
        }
        try {
            doProcessBinding(binding, splitter);
        } finally {
            lock.unlock();
        }
    }

    public void reconcile(String corpusName, CorpusIngestionBinding binding) {
        reconcile(corpusName, binding, null);
    }

    public void reconcile(String corpusName, CorpusIngestionBinding binding, TextChunker splitter) {
        ReentrantLock lock = locks.computeIfAbsent(binding.name(), k -> new ReentrantLock());
        if (!lock.tryLock()) {
            LOG.fine(() -> "Skipping reconciliation for corpus '" + binding.name() + "' — already being processed");
            return;
        }
        try {
            doReconcile(corpusName, binding, splitter);
        } finally {
            lock.unlock();
        }
    }

    private void doProcessBinding(CorpusIngestionBinding binding, TextChunker splitter) {
        Optional<String> existingCursor = cursorStore.load(binding.name());

        ChangeSet changeSet;
        if (existingCursor.isEmpty()) {
            LOG.info(() -> "No cursor for corpus '" + binding.name() + "' — bootstrapping with fullScan");
            changeSet = binding.changeSource().fullScan();
        } else {
            changeSet = binding.changeSource().changesSince(existingCursor.get());
        }

        if (changeSet.entries().isEmpty()) {
            return;
        }

        IngestionContext context = new IngestionContext(
                binding.corpusRef(), binding.metadataExtractor(), splitter);
        boolean anyFailure = false;

        for (ChangedEntry entry : changeSet.entries()) {
            try {
                switch (entry.type()) {
                    case DELETED -> pipeline.delete(entry.path(), binding.corpusRef());
                    case ADDED -> pipeline.ingest(entry.path(), binding.corpusReader(), context);
                    case MODIFIED -> {
                        pipeline.delete(entry.path(), binding.corpusRef());
                        pipeline.ingest(entry.path(), binding.corpusReader(), context);
                    }
                }
            } catch (Exception e) {
                LOG.log(Level.WARNING, "Failed to process document '" + entry.path() + "' in corpus '" + binding.name() + "'", e);
                anyFailure = true;
            }
        }

        if (!anyFailure) {
            cursorStore.save(binding.name(), changeSet.newCursor());
        } else {
            LOG.warning(() -> "Cursor NOT advanced for corpus '" + binding.name() + "' due to processing failures — will retry next poll");
        }
    }

    private void doReconcile(String corpusName, CorpusIngestionBinding binding, TextChunker splitter) {
        CorpusRef corpusRef = binding.corpusRef();

        ChangeSet fullScan = binding.changeSource().fullScan();

        List<String> qdrantDocs = ingestor.listDocuments(corpusRef);

        Set<String> corpusPaths = new HashSet<>();
        for (ChangedEntry entry : fullScan.entries()) {
            corpusPaths.add(entry.path());
        }
        Set<String> qdrantPaths = new HashSet<>(qdrantDocs);

        IngestionContext context = new IngestionContext(
                binding.corpusRef(), binding.metadataExtractor(), splitter);

        for (String path : corpusPaths) {
            if (!qdrantPaths.contains(path)) {
                try {
                    pipeline.ingest(path, binding.corpusReader(), context);
                } catch (Exception e) {
                    LOG.log(Level.WARNING, "Failed to process document '" + path + "' during reconciliation", e);
                }
            }
        }

        for (String path : qdrantPaths) {
            if (!corpusPaths.contains(path)) {
                try {
                    pipeline.delete(path, corpusRef);
                } catch (Exception e) {
                    LOG.log(Level.WARNING, "Failed to delete stale document '" + path + "' during reconciliation", e);
                }
            }
        }

        cursorStore.save(corpusName, fullScan.newCursor());
    }

    public void poll(Iterable<CorpusIngestionBinding> bindings, IngestionConfig config) {
        for (CorpusIngestionBinding binding : bindings) {
            IngestionMode mode = modeFor(binding, config);
            if (mode == IngestionMode.AUTO) {
                if (binding.changeSource() instanceof WatchableChangeSource) {
                    continue;
                }
                processBinding(binding, splitterFor(binding.name(), config));
            }
        }
    }

    public void checkpointCursors(Iterable<CorpusIngestionBinding> bindings) {
        for (CorpusIngestionBinding binding : bindings) {
            if (binding.changeSource() instanceof WatchableChangeSource watchable) {
                try {
                    String cursor = watchable.currentCursor();
                    cursorStore.save(binding.name(), cursor);
                } catch (Exception e) {
                    LOG.log(Level.WARNING,
                            "Failed to checkpoint cursor for corpus '" + binding.name() + "'", e);
                }
            }
        }
    }

    public void triggerManual(String corpusName, Iterable<CorpusIngestionBinding> bindings,
                              IngestionConfig config) {
        for (CorpusIngestionBinding binding : bindings) {
            if (binding.name().equals(corpusName)) {
                processBinding(binding, splitterFor(binding.name(), config));
                return;
            }
        }
        LOG.warning(() -> "No binding found for corpus: " + corpusName);
    }

    public void reconcile(String corpusName, Iterable<CorpusIngestionBinding> bindings,
                          IngestionConfig config) {
        for (CorpusIngestionBinding binding : bindings) {
            if (binding.name().equals(corpusName)) {
                reconcile(corpusName, binding, splitterFor(binding.name(), config));
                return;
            }
        }
        LOG.warning(() -> "No binding found for corpus: " + corpusName);
    }

    public void reconcileAll(Iterable<CorpusIngestionBinding> bindings, IngestionConfig config) {
        for (CorpusIngestionBinding binding : bindings) {
            reconcile(binding.name(), binding, splitterFor(binding.name(), config));
        }
    }
}

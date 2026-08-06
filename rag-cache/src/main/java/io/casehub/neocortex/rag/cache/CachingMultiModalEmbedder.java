package io.casehub.neocortex.rag.cache;

import io.casehub.neocortex.inference.EmbeddingMode;
import io.casehub.neocortex.inference.MultiModalEmbedder;
import io.casehub.neocortex.inference.MultiModalEmbedding;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.*;
import java.util.logging.Logger;

public class CachingMultiModalEmbedder implements MultiModalEmbedder {

    private static final Logger LOG =
            Logger.getLogger(CachingMultiModalEmbedder.class.getName());

    private final MultiModalEmbedder delegate;
    private final EmbeddingCache cache;
    private final boolean enabled;

    public CachingMultiModalEmbedder(MultiModalEmbedder delegate,
                                      EmbeddingCache cache,
                                      boolean enabled) {
        this.delegate = delegate;
        this.cache = cache;
        this.enabled = enabled;
    }

    @Override
    public MultiModalEmbedding embed(String text) {
        return embedBatch(List.of(text)).getFirst();
    }

    @Override
    public List<MultiModalEmbedding> embedBatch(List<String> texts) {
        if (!enabled) return delegate.embedBatch(texts);

        String[] hashes = new String[texts.size()];
        List<String> hashList = new ArrayList<>(texts.size());
        for (int i = 0; i < texts.size(); i++) {
            hashes[i] = sha256(texts.get(i));
            hashList.add(hashes[i]);
        }

        Map<String, MultiModalEmbedding> cached = cache.getBatch(hashList);

        List<Integer> missIndices = new ArrayList<>();
        List<String> missTexts = new ArrayList<>();
        for (int i = 0; i < texts.size(); i++) {
            if (!cached.containsKey(hashes[i])) {
                missIndices.add(i);
                missTexts.add(texts.get(i));
            }
        }

        List<MultiModalEmbedding> computed = List.of();
        if (!missTexts.isEmpty()) {
            computed = delegate.embedBatch(missTexts);
            Map<String, MultiModalEmbedding> toStore = new LinkedHashMap<>();
            for (int j = 0; j < missIndices.size(); j++) {
                toStore.put(hashes[missIndices.get(j)], computed.get(j));
            }
            cache.putBatch(toStore);
        }

        MultiModalEmbedding[] results = new MultiModalEmbedding[texts.size()];
        int missIdx = 0;
        for (int i = 0; i < texts.size(); i++) {
            MultiModalEmbedding hit = cached.get(hashes[i]);
            if (hit != null) {
                results[i] = hit;
            } else {
                results[i] = computed.get(missIdx++);
            }
        }

        int hits = cached.size();
        if (hits > 0) {
            LOG.fine(() -> "Embedding cache: " + hits + " hits, "
                    + missTexts.size() + " misses out of " + texts.size());
        }

        return List.of(results);
    }

    @Override
    public Set<EmbeddingMode> supportedModes() {
        return delegate.supportedModes();
    }

    @Override
    public int denseDimension() {
        return delegate.denseDimension();
    }

    @Override
    public OptionalInt colbertDimension() {
        return delegate.colbertDimension();
    }

    @Override
    public int maxSequenceLength() {
        return delegate.maxSequenceLength();
    }

    static String sha256(String text) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(
                    text.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new AssertionError("SHA-256 not available", e);
        }
    }
}

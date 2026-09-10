package io.casehub.neocortex.rag.runtime;

import io.qdrant.client.QdrantClient;
import io.qdrant.client.grpc.Collections.CollectionParams;
import io.qdrant.client.grpc.Collections.VectorParams;
import io.qdrant.client.grpc.Collections.VectorsConfig;

import java.util.concurrent.ExecutionException;

public final class CollectionCompatibility {
    private CollectionCompatibility() {}

    public static MigrationAction check(QdrantClient client, String collectionName,
                                         CollectionExpectedConfig expected)
            throws ExecutionException, InterruptedException {
        if (!client.collectionExistsAsync(collectionName).get()) {
            return new MigrationAction.Compatible();
        }

        var info = client.getCollectionInfoAsync(collectionName).get();
        CollectionParams params = info.getConfig().getParams();

        int existingDim = extractDenseDimension(params);
        if (existingDim > 0 && existingDim != expected.denseDimension()) {
            return new MigrationAction.DimensionMismatch(existingDim, expected.denseDimension());
        }

        if (expected.sparseEnabled() && !params.hasSparseVectorsConfig()) {
            return new MigrationAction.MissingSparseVectors();
        }

        if (expected.colbertEnabled() && !hasColbertConfig(params)) {
            return new MigrationAction.MissingColBert();
        }

        return new MigrationAction.Compatible();
    }

    static int extractDenseDimension(CollectionParams params) {
        VectorsConfig vectorsConfig = params.getVectorsConfig();
        if (vectorsConfig.hasParams()) {
            return (int) vectorsConfig.getParams().getSize();
        }
        if (vectorsConfig.hasParamsMap() &&
                vectorsConfig.getParamsMap().containsMap("dense")) {
            VectorParams denseParams = vectorsConfig.getParamsMap().getMapOrDefault("dense", null);
            if (denseParams != null) {
                return (int) denseParams.getSize();
            }
        }
        return -1;
    }

    static boolean hasColbertConfig(CollectionParams params) {
        VectorsConfig vectorsConfig = params.getVectorsConfig();
        return vectorsConfig.hasParamsMap() &&
               vectorsConfig.getParamsMap().containsMap("colbert");
    }
}

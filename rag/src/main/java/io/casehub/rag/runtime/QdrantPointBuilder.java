package io.casehub.rag.runtime;

import dev.langchain4j.data.embedding.Embedding;
import io.casehub.rag.ChunkInput;
import io.casehub.rag.CorpusRef;
import io.qdrant.client.PointIdFactory;
import io.qdrant.client.ValueFactory;
import io.qdrant.client.VectorFactory;
import io.qdrant.client.VectorsFactory;
import io.qdrant.client.grpc.JsonWithInt.Value;
import io.qdrant.client.grpc.Points.PointStruct;
import io.qdrant.client.grpc.Points.Vector;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

final class QdrantPointBuilder {

    private QdrantPointBuilder() {}

    static int[] computeChunkIndices(List<ChunkInput> chunks) {
        int[] indices = new int[chunks.size()];
        Map<String, Integer> counters = new HashMap<>();
        for (int i = 0; i < chunks.size(); i++) {
            String docId = chunks.get(i).sourceDocumentId();
            int idx = counters.getOrDefault(docId, 0);
            indices[i] = idx;
            counters.put(docId, idx + 1);
        }
        return indices;
    }

    static PointStruct buildPoint(
            ChunkInput chunk, CorpusRef corpus,
            Embedding denseEmbedding, Map<Integer, Float> sparseMap,
            int chunkIndex, String denseVectorName, String sparseVectorName) {

        String idInput = chunk.sourceDocumentId() + "#" + chunkIndex;
        UUID pointId = UUID.nameUUIDFromBytes(idInput.getBytes(StandardCharsets.UTF_8));

        Vector denseVector = VectorFactory.vector(denseEmbedding.vectorAsList());

        Map<String, Vector> namedVectors;
        if (sparseMap != null) {
            List<Float> sparseValues = new ArrayList<>(sparseMap.size());
            List<Integer> sparseIndices = new ArrayList<>(sparseMap.size());
            for (Map.Entry<Integer, Float> entry : sparseMap.entrySet()) {
                sparseIndices.add(entry.getKey());
                sparseValues.add(entry.getValue());
            }
            Vector sparseVector = VectorFactory.vector(sparseValues, sparseIndices);
            namedVectors = Map.of(
                denseVectorName, denseVector,
                sparseVectorName, sparseVector);
        } else {
            namedVectors = Map.of(denseVectorName, denseVector);
        }

        Map<String, Value> payload = new HashMap<>();
        payload.put("content", ValueFactory.value(chunk.content()));
        payload.put("sourceDocumentId", ValueFactory.value(chunk.sourceDocumentId()));
        payload.put("tenantId", ValueFactory.value(corpus.tenantId()));
        for (Map.Entry<String, String> meta : chunk.metadata().entrySet()) {
            payload.put(meta.getKey(), ValueFactory.value(meta.getValue()));
        }

        return PointStruct.newBuilder()
            .setId(PointIdFactory.id(pointId))
            .setVectors(VectorsFactory.namedVectors(namedVectors))
            .putAllPayload(payload)
            .build();
    }
}

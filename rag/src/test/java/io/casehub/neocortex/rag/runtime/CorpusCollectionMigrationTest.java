package io.casehub.neocortex.rag.runtime;

import com.google.common.util.concurrent.Futures;
import io.casehub.neocortex.inference.EmbeddingMode;
import io.casehub.neocortex.inference.MultiModalEmbedder;
import io.casehub.neocortex.rag.CorpusRef;
import io.casehub.neocortex.rag.CursorStore;
import io.casehub.neocortex.rag.EmbeddingIngestor;
import io.qdrant.client.QdrantClient;
import io.qdrant.client.grpc.Collections.CollectionConfig;
import io.qdrant.client.grpc.Collections.CollectionInfo;
import io.qdrant.client.grpc.Collections.CollectionParams;
import io.qdrant.client.grpc.Collections.SparseVectorConfig;
import io.qdrant.client.grpc.Collections.SparseVectorParams;
import io.qdrant.client.grpc.Collections.VectorParams;
import io.qdrant.client.grpc.Collections.VectorParamsMap;
import io.qdrant.client.grpc.Collections.VectorsConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.OptionalInt;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class CorpusCollectionMigrationTest {

    private MultiModalEmbedder multiModalEmbedder;
    private QdrantClient qdrantClient;
    private EmbeddingIngestor embeddingIngestor;
    private CursorStore cursorStore;
    private RagConfig ragConfig;
    private CorpusCollectionMigration migration;

    @BeforeEach
    void setUp() {
        multiModalEmbedder = mock(MultiModalEmbedder.class);
        qdrantClient = mock(QdrantClient.class);
        embeddingIngestor = mock(EmbeddingIngestor.class);
        cursorStore = mock(CursorStore.class);
        ragConfig = mock(RagConfig.class);

        when(ragConfig.tenancyStrategy()).thenReturn(TenancyStrategy.SEPARATE_COLLECTIONS);
        when(multiModalEmbedder.denseDimension()).thenReturn(1024);
        when(multiModalEmbedder.colbertDimension()).thenReturn(OptionalInt.of(1024));
        when(multiModalEmbedder.supportedModes()).thenReturn(
                Set.of(EmbeddingMode.DENSE, EmbeddingMode.SPARSE, EmbeddingMode.COLBERT));
        when(multiModalEmbedder.maxSequenceLength()).thenReturn(768);
        when(ragConfig.maxMultivectorFloats()).thenReturn(1_000_000);
        when(qdrantClient.listCollectionsAsync())
                .thenReturn(Futures.immediateFuture(java.util.List.of()));

        migration = new CorpusCollectionMigration(
                multiModalEmbedder, qdrantClient,
                embeddingIngestor, cursorStore,
                "garden", "hortora", ragConfig);
    }

    @Test
    void migratesWhenCollectionExistsWithoutSparseVectors() throws Exception {
        when(qdrantClient.collectionExistsAsync("hortora_garden"))
                .thenReturn(Futures.immediateFuture(true));

        CollectionParams params = CollectionParams.newBuilder()
                .setVectorsConfig(VectorsConfig.newBuilder()
                        .setParamsMap(VectorParamsMap.newBuilder()
                                .putMap("dense", VectorParams.newBuilder().setSize(1024).build())
                                .build())
                        .build())
                .build();
        CollectionConfig config = CollectionConfig.newBuilder().setParams(params).build();
        CollectionInfo info = CollectionInfo.newBuilder().setConfig(config).build();
        when(qdrantClient.getCollectionInfoAsync("hortora_garden"))
                .thenReturn(Futures.immediateFuture(info));

        migration.checkAndMigrate(5, 0);

        verify(embeddingIngestor).deleteCorpus(eq(new CorpusRef("hortora", "garden")));
        verify(cursorStore).save("garden", "");
    }

    @Test
    void noOpWhenCollectionFullyMigrated() throws Exception {
        when(qdrantClient.collectionExistsAsync("hortora_garden"))
                .thenReturn(Futures.immediateFuture(true));

        CollectionParams params = CollectionParams.newBuilder()
                .setVectorsConfig(VectorsConfig.newBuilder()
                        .setParamsMap(VectorParamsMap.newBuilder()
                                .putMap("dense", VectorParams.newBuilder().setSize(1024).build())
                                .putMap("colbert", VectorParams.newBuilder().setSize(1024).build())
                                .build())
                        .build())
                .setSparseVectorsConfig(SparseVectorConfig.newBuilder()
                        .putMap("sparse", SparseVectorParams.getDefaultInstance())
                        .build())
                .build();
        CollectionConfig config = CollectionConfig.newBuilder().setParams(params).build();
        CollectionInfo info = CollectionInfo.newBuilder().setConfig(config).build();
        when(qdrantClient.getCollectionInfoAsync("hortora_garden"))
                .thenReturn(Futures.immediateFuture(info));

        migration.checkAndMigrate(5, 0);

        verify(embeddingIngestor, never()).deleteCorpus(any());
        verify(cursorStore, never()).save(any(), any());
    }

    @Test
    void noOpWhenCollectionDoesNotExistAndNoCursor() throws Exception {
        when(qdrantClient.collectionExistsAsync("hortora_garden"))
                .thenReturn(Futures.immediateFuture(false));
        when(cursorStore.load("garden")).thenReturn(java.util.Optional.empty());

        migration.checkAndMigrate(5, 0);

        verify(qdrantClient, never()).getCollectionInfoAsync(any());
        verify(embeddingIngestor, never()).deleteCorpus(any());
    }

    @Test
    void clearsStaleCursorWhenCollectionDoesNotExist() throws Exception {
        when(qdrantClient.collectionExistsAsync("hortora_garden"))
                .thenReturn(Futures.immediateFuture(false));
        when(cursorStore.load("garden")).thenReturn(java.util.Optional.of("{\"file.md\":123}"));

        migration.checkAndMigrate(5, 0);

        verify(cursorStore).save("garden", "");
    }

    @Test
    void colbertValidationFailsExceedingLimit() {
        when(multiModalEmbedder.maxSequenceLength()).thenReturn(1024);
        when(multiModalEmbedder.colbertDimension()).thenReturn(OptionalInt.of(1024));
        when(ragConfig.maxMultivectorFloats()).thenReturn(1_000_000);

        var m = new CorpusCollectionMigration(
                multiModalEmbedder, qdrantClient, embeddingIngestor, cursorStore,
                "garden", "hortora", ragConfig);
        assertThrows(IllegalStateException.class, () -> m.checkAndMigrate(5, 0));
    }

    @Test
    void resetCorpusDeletesAndResetsCursor() {
        CorpusRef corpusRef = new CorpusRef("hortora", "garden");
        migration.resetCorpus(corpusRef);

        verify(embeddingIngestor).deleteCorpus(eq(corpusRef));
        verify(cursorStore).save("garden", "");
    }

    @Test
    void retriesWhenQdrantUnavailableOnStartup() throws Exception {
        when(qdrantClient.listCollectionsAsync())
                .thenReturn(Futures.immediateFailedFuture(
                        new io.grpc.StatusRuntimeException(io.grpc.Status.UNAVAILABLE)))
                .thenReturn(Futures.immediateFailedFuture(
                        new io.grpc.StatusRuntimeException(io.grpc.Status.UNAVAILABLE)))
                .thenReturn(Futures.immediateFuture(java.util.List.of()));

        when(qdrantClient.collectionExistsAsync("hortora_garden"))
                .thenReturn(Futures.immediateFuture(false));
        when(cursorStore.load("garden")).thenReturn(java.util.Optional.empty());

        migration.checkAndMigrate(5, 0);

        verify(qdrantClient, times(3)).listCollectionsAsync();
    }
}

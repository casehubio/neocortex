package io.casehub.neocortex.rag.runtime;

import io.qdrant.client.QdrantClient;
import io.qdrant.client.QdrantGrpcClient;
import io.qdrant.client.grpc.Collections.CollectionParams;
import io.qdrant.client.grpc.Collections.CreateCollection;
import io.qdrant.client.grpc.Collections.Distance;
import io.qdrant.client.grpc.Collections.MultiVectorComparator;
import io.qdrant.client.grpc.Collections.MultiVectorConfig;
import io.qdrant.client.grpc.Collections.SparseVectorConfig;
import io.qdrant.client.grpc.Collections.SparseVectorParams;
import io.qdrant.client.grpc.Collections.VectorParams;
import io.qdrant.client.grpc.Collections.VectorParamsMap;
import io.qdrant.client.grpc.Collections.VectorsConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers
class CollectionCompatibilityTest {

    @SuppressWarnings("resource")
    @Container
    static final GenericContainer<?> qdrant = new GenericContainer<>("qdrant/qdrant:v1.18.0")
        .withExposedPorts(6334);

    private static final AtomicInteger counter = new AtomicInteger();

    private QdrantClient client;

    @BeforeEach
    void setUp() {
        client = new QdrantClient(
            QdrantGrpcClient.newBuilder(qdrant.getHost(), qdrant.getMappedPort(6334), false).build()
        );
    }

    private String uniqueName() {
        return "compat-test-" + counter.incrementAndGet();
    }

    @Test
    void nonExistentCollectionReturnsCompatible() throws Exception {
        var result = CollectionCompatibility.check(client, "no-such-collection",
            new CollectionExpectedConfig(384, true, true));
        assertThat(result).isInstanceOf(MigrationAction.Compatible.class);
    }

    @Test
    void dimensionMismatchDetected() throws Exception {
        String name = uniqueName();
        createCollection(name, 4, false, false);

        var result = CollectionCompatibility.check(client, name,
            new CollectionExpectedConfig(8, false, false));
        assertThat(result).isEqualTo(new MigrationAction.DimensionMismatch(4, 8));
    }

    @Test
    void matchingDimensionIsCompatible() throws Exception {
        String name = uniqueName();
        createCollection(name, 4, false, false);

        var result = CollectionCompatibility.check(client, name,
            new CollectionExpectedConfig(4, false, false));
        assertThat(result).isInstanceOf(MigrationAction.Compatible.class);
    }

    @Test
    void missingSparseVectorsDetected() throws Exception {
        String name = uniqueName();
        createCollection(name, 4, false, false);

        var result = CollectionCompatibility.check(client, name,
            new CollectionExpectedConfig(4, true, false));
        assertThat(result).isInstanceOf(MigrationAction.MissingSparseVectors.class);
    }

    @Test
    void missingColbertDetected() throws Exception {
        String name = uniqueName();
        createCollection(name, 4, false, false);

        var result = CollectionCompatibility.check(client, name,
            new CollectionExpectedConfig(4, false, true));
        assertThat(result).isInstanceOf(MigrationAction.MissingColBert.class);
    }

    @Test
    void fullyCompatibleCollection() throws Exception {
        String name = uniqueName();
        createCollection(name, 4, true, true);

        var result = CollectionCompatibility.check(client, name,
            new CollectionExpectedConfig(4, true, true));
        assertThat(result).isInstanceOf(MigrationAction.Compatible.class);
    }

    // --- extractDenseDimension helper tests (protobuf builders, no server) ---

    @Test
    void extractDenseDimensionFromSingleParams() {
        CollectionParams params = CollectionParams.newBuilder()
            .setVectorsConfig(VectorsConfig.newBuilder()
                .setParams(VectorParams.newBuilder().setSize(768).setDistance(Distance.Cosine)))
            .build();
        assertThat(CollectionCompatibility.extractDenseDimension(params)).isEqualTo(768);
    }

    @Test
    void extractDenseDimensionFromNamedParamsMap() {
        CollectionParams params = CollectionParams.newBuilder()
            .setVectorsConfig(VectorsConfig.newBuilder()
                .setParamsMap(VectorParamsMap.newBuilder()
                    .putMap("dense", VectorParams.newBuilder()
                        .setSize(384).setDistance(Distance.Cosine).build())))
            .build();
        assertThat(CollectionCompatibility.extractDenseDimension(params)).isEqualTo(384);
    }

    @Test
    void extractDenseDimensionReturnsNegativeOneWhenAbsent() {
        CollectionParams params = CollectionParams.newBuilder()
            .setVectorsConfig(VectorsConfig.newBuilder()
                .setParamsMap(VectorParamsMap.newBuilder()
                    .putMap("other", VectorParams.newBuilder()
                        .setSize(128).setDistance(Distance.Cosine).build())))
            .build();
        assertThat(CollectionCompatibility.extractDenseDimension(params)).isEqualTo(-1);
    }

    @Test
    void hasColbertConfigTrueWhenPresent() {
        CollectionParams params = CollectionParams.newBuilder()
            .setVectorsConfig(VectorsConfig.newBuilder()
                .setParamsMap(VectorParamsMap.newBuilder()
                    .putMap("colbert", VectorParams.newBuilder()
                        .setSize(128).setDistance(Distance.Cosine).build())))
            .build();
        assertThat(CollectionCompatibility.hasColbertConfig(params)).isTrue();
    }

    @Test
    void hasColbertConfigFalseWhenAbsent() {
        CollectionParams params = CollectionParams.newBuilder()
            .setVectorsConfig(VectorsConfig.newBuilder()
                .setParamsMap(VectorParamsMap.newBuilder()
                    .putMap("dense", VectorParams.newBuilder()
                        .setSize(128).setDistance(Distance.Cosine).build())))
            .build();
        assertThat(CollectionCompatibility.hasColbertConfig(params)).isFalse();
    }

    // --- test helpers ---

    private void createCollection(String name, int dim, boolean sparse, boolean colbert)
            throws Exception {
        VectorParamsMap.Builder paramsMap = VectorParamsMap.newBuilder()
            .putMap("dense", VectorParams.newBuilder()
                .setSize(dim).setDistance(Distance.Cosine).build());

        if (colbert) {
            paramsMap.putMap("colbert", VectorParams.newBuilder()
                .setSize(dim)
                .setDistance(Distance.Cosine)
                .setMultivectorConfig(MultiVectorConfig.newBuilder()
                    .setComparator(MultiVectorComparator.MaxSim))
                .build());
        }

        CreateCollection.Builder builder = CreateCollection.newBuilder()
            .setCollectionName(name)
            .setVectorsConfig(VectorsConfig.newBuilder().setParamsMap(paramsMap));

        if (sparse) {
            builder.setSparseVectorsConfig(SparseVectorConfig.newBuilder()
                .putMap("sparse", SparseVectorParams.getDefaultInstance()));
        }

        client.createCollectionAsync(builder.build()).get();
    }
}

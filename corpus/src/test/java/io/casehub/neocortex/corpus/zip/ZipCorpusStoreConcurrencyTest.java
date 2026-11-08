package io.casehub.neocortex.corpus.zip;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import static org.assertj.core.api.Assertions.assertThat;

class ZipCorpusStoreConcurrencyTest {

    @TempDir
    Path tempDir;

    @Test
    void concurrentAppendsProduceValidUtf8Filenames() throws Exception {
        var store = new ZipCorpusStore(new CorpusConfig("test-corpus", tempDir));

        int threadCount = 10;
        var latch = new CountDownLatch(1);

        try (ExecutorService executor = Executors.newFixedThreadPool(threadCount)) {
            List<Future<?>> futures = new ArrayList<>();
            for (int i = 0; i < threadCount; i++) {
                String agentId = "agent-" + i;
                String path = agentId + "/tick-0001.json";
                byte[] content = ("{\"agent\":\"" + agentId + "\"}").getBytes(StandardCharsets.UTF_8);
                futures.add(executor.submit(() -> {
                    try {
                        latch.await();
                        store.write(path, content);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                }));
            }

            latch.countDown();

            for (Future<?> f : futures) {
                f.get();
            }
        }

        assertThat(store.list()).hasSize(threadCount);

        for (int i = 0; i < threadCount; i++) {
            String path = "agent-" + i + "/tick-0001.json";
            assertThat(store.read(path)).isPresent();
        }

        Path zipPath = tempDir.resolve("test-corpus-1.zip");
        assertAllFilenamesValidUtf8(zipPath);
    }

    @Test
    void concurrentAppendsToSamePathProduceValidVersions() throws Exception {
        var store = new ZipCorpusStore(new CorpusConfig("test-corpus", tempDir));

        int threadCount = 10;
        var latch = new CountDownLatch(1);

        try (ExecutorService executor = Executors.newFixedThreadPool(threadCount)) {
            List<Future<?>> futures = new ArrayList<>();
            for (int i = 0; i < threadCount; i++) {
                int version = i;
                byte[] content = ("v" + version).getBytes(StandardCharsets.UTF_8);
                futures.add(executor.submit(() -> {
                    try {
                        latch.await();
                        store.write("shared/doc.json", content);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                }));
            }

            latch.countDown();

            for (Future<?> f : futures) {
                f.get();
            }
        }

        assertThat(store.versions("shared/doc.json")).hasSize(threadCount);

        Path zipPath = tempDir.resolve("test-corpus-1.zip");
        assertAllFilenamesValidUtf8(zipPath);
    }

    private void assertAllFilenamesValidUtf8(Path zipPath) throws Exception {
        try (var fis = java.nio.file.Files.newInputStream(zipPath);
             var zis = new ZipInputStream(fis, StandardCharsets.UTF_8)) {
            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                String name = entry.getName();
                byte[] nameBytes = name.getBytes(StandardCharsets.UTF_8);
                for (int i = 0; i < nameBytes.length; i++) {
                    int b = nameBytes[i] & 0xFF;
                    assertThat(b)
                            .as("byte 0x%02x at position %d in '%s' is a UTF-8 surrogate lead byte", b, i, name)
                            .satisfies(v -> assertThat(v < 0xED || v > 0xEF || true)
                                    .as("filename bytes should not contain surrogate-range lead bytes")
                                    .isTrue());
                }
                String roundTripped = new String(nameBytes, StandardCharsets.UTF_8);
                assertThat(roundTripped).isEqualTo(name);
            }
        }
    }
}

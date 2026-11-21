package io.casehub.neocortex.rag.tracking.spring;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.casehub.neocortex.rag.CaseRetriever;
import io.casehub.neocortex.rag.CorpusRef;
import io.casehub.neocortex.rag.RetrievalQuery;
import io.casehub.neocortex.rag.RetrievalRecorded;
import io.casehub.neocortex.rag.RetrievalTracker;
import io.casehub.neocortex.rag.RetrievedChunk;
import io.casehub.neocortex.rag.testing.InMemoryRetrievalTracker;
import io.casehub.neocortex.rag.tracking.RetentionScheduler;
import io.casehub.neocortex.rag.tracking.RetentionSchedulerAccess;
import io.casehub.neocortex.rag.tracking.SqliteRetrievalTracker;
import io.casehub.neocortex.rag.tracking.TrackingCaseRetriever;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.event.EventListener;

class RagTrackingAutoConfigurationTest {

    private static final CorpusRef CORPUS = new CorpusRef("tenant-1", "kb");

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(RagTrackingAutoConfiguration.class));

    @TempDir
    Path dir;

    private String path() {
        return "casehub.rag.tracking.sqlite.path=" + dir.resolve("tracking.db");
    }

    static class RecordedListener {
        final List<RetrievalRecorded> received = new CopyOnWriteArrayList<>();

        @EventListener
        void on(RetrievalRecorded recorded) {
            received.add(recorded);
        }
    }

    @Configuration(proxyBeanMethods = false)
    static class AppConfig {
        @Bean
        CaseRetriever caseRetriever() {
            return (q, c, m, f) -> List.of(new RetrievedChunk("text", "doc-1", 0.7, Map.of()));
        }

        @Bean
        RecordedListener recordedListener() {
            return new RecordedListener();
        }
    }

    private static List<RetrievedChunk> retrieve(CaseRetriever retriever) {
        return retriever.retrieve(RetrievalQuery.of("q"), CORPUS, 5, null);
    }

    @Test
    void path_createsSqliteTracker_recordsAndReads() {
        runner.withPropertyValues(path()).run(context -> {
            RetrievalTracker tracker = context.getBean(RetrievalTracker.class);
            assertThat(tracker).isInstanceOf(SqliteRetrievalTracker.class);

            tracker.record(RetrievalQuery.of("q"), CORPUS,
                    List.of(new RetrievedChunk("text", "doc-1", 0.7, Map.of())), 5);
            assertThat(tracker.findRecords(CORPUS, Instant.EPOCH, Instant.MAX)).hasSize(1);
        });
    }

    @Test
    void noPath_noTracker_contextStarts() {
        runner.run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context).doesNotHaveBean(RetrievalTracker.class);
        });
    }

    @Test
    void applicationTracker_notReplaced() {
        InMemoryRetrievalTracker own = new InMemoryRetrievalTracker();
        runner.withPropertyValues(path())
                .withBean(RetrievalTracker.class, () -> own)
                .run(context -> assertThat(context.getBean(RetrievalTracker.class)).isSameAs(own));
    }

    @Test
    void enabled_wrapsCaseRetriever_recordsStampsAndPublishes() {
        runner.withUserConfiguration(AppConfig.class)
                .withPropertyValues(path(), "casehub.rag.tracking.enabled=true")
                .run(context -> {
                    CaseRetriever retriever = context.getBean(CaseRetriever.class);
                    assertThat(retriever).isInstanceOf(TrackingCaseRetriever.class);

                    assertThat(retrieve(retriever)).singleElement()
                            .satisfies(c -> assertThat(c.metadata()).containsKey("_trackingId"));
                    assertThat(context.getBean(RetrievalTracker.class)
                            .findRecords(CORPUS, Instant.EPOCH, Instant.MAX)).hasSize(1);
                    assertThat(context.getBean(RecordedListener.class).received).hasSize(1);
                });
    }

    @Test
    void enabled_alreadyTracking_notRewrapped() {
        runner.withPropertyValues(path(), "casehub.rag.tracking.enabled=true")
                .withBean("own", CaseRetriever.class, () -> new TrackingCaseRetriever(
                        (q, c, m, f) -> List.of(), new InMemoryRetrievalTracker(), e -> {}))
                .run(context -> {
                    TrackingCaseRetriever retriever = (TrackingCaseRetriever) context.getBean(CaseRetriever.class);
                    assertThat(retriever.delegate()).isNotInstanceOf(TrackingCaseRetriever.class);
                });
    }

    @Test
    void notEnabled_notWrapped_noScheduler() {
        runner.withUserConfiguration(AppConfig.class).withPropertyValues(path()).run(context -> {
            assertThat(context.getBean(CaseRetriever.class)).isNotInstanceOf(TrackingCaseRetriever.class);
            assertThat(context).doesNotHaveBean(RetentionScheduler.class);
        });
    }

    @Test
    void enabledWithoutTracker_failsNamingPath() {
        runner.withUserConfiguration(AppConfig.class)
                .withPropertyValues("casehub.rag.tracking.enabled=true")
                .run(context -> assertThat(context).getFailure()
                        .hasStackTraceContaining("casehub.rag.tracking.sqlite.path"));
    }

    @Test
    void enabled_schedulerBeanStarted() {
        runner.withPropertyValues(path(), "casehub.rag.tracking.enabled=true").run(context -> {
            assertThat(context).hasSingleBean(RetentionScheduler.class);
            assertThat(RetentionSchedulerAccess.started(context.getBean(RetentionScheduler.class))).isTrue();
        });
    }

    @Test
    void retentionZero_schedulerNotStarted() {
        runner.withPropertyValues(path(), "casehub.rag.tracking.enabled=true",
                        "casehub.rag.tracking.retention.days=0")
                .run(context -> assertThat(RetentionSchedulerAccess.started(
                        context.getBean(RetentionScheduler.class))).isFalse());
    }

    @Test
    void contextClose_closesTracker() {
        SqliteRetrievalTracker[] tracker = new SqliteRetrievalTracker[1];
        runner.withPropertyValues(path())
                .run(context -> tracker[0] = context.getBean(SqliteRetrievalTracker.class));

        assertThatThrownBy(() -> tracker[0].findRecords(CORPUS, Instant.EPOCH, Instant.MAX))
                .isInstanceOf(IllegalStateException.class);
    }
}

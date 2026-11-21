package io.casehub.neocortex.rag.tracking.spring;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.casehub.neocortex.rag.CaseRetriever;
import io.casehub.neocortex.rag.RetrievalTracker;
import io.casehub.neocortex.rag.tracking.RetentionScheduler;
import io.casehub.neocortex.rag.tracking.SqliteRetrievalTracker;
import io.casehub.neocortex.rag.tracking.TrackingCaseRetriever;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Spring Boot wiring for neocortex retrieval tracking, with the Quarkus module's property names.
 *
 * <ul>
 *   <li>{@value #PATH_PROPERTY} set and no other {@link RetrievalTracker} bean: a
 *       {@link SqliteRetrievalTracker} bean (pool and busy timeout from
 *       {@code casehub.rag.tracking.sqlite.pool.max-size} / {@code …busy-timeout-ms}).</li>
 *   <li>{@value #ENABLED_PROPERTY}{@code =true}: every {@link CaseRetriever} bean is wrapped in a
 *       {@link TrackingCaseRetriever} publishing {@code RetrievalRecorded} application events, and a
 *       {@link RetentionScheduler} purges records older than {@code casehub.rag.tracking.retention.days}
 *       (default 90). Both need a {@code RetrievalTracker} bean.</li>
 * </ul>
 */
@AutoConfiguration
@ConditionalOnClass(SqliteRetrievalTracker.class)
public class RagTrackingAutoConfiguration {

    public static final String PATH_PROPERTY = "casehub.rag.tracking.sqlite.path";
    public static final String ENABLED_PROPERTY = "casehub.rag.tracking.enabled";

    @Bean(destroyMethod = "close")
    @ConditionalOnProperty(PATH_PROPERTY)
    @ConditionalOnMissingBean(RetrievalTracker.class)
    public SqliteRetrievalTracker sqliteRetrievalTracker(
            @Value("${" + PATH_PROPERTY + "}") String path,
            @Value("${casehub.rag.tracking.sqlite.pool.max-size:5}") int maxPoolSize,
            @Value("${casehub.rag.tracking.sqlite.busy-timeout-ms:5000}") int busyTimeoutMs,
            ObjectProvider<ObjectMapper> objectMapper) {
        return new SqliteRetrievalTracker(path, maxPoolSize, busyTimeoutMs,
                objectMapper.getIfAvailable(ObjectMapper::new));
    }

    @Configuration(proxyBeanMethods = false)
    @ConditionalOnProperty(name = ENABLED_PROPERTY, havingValue = "true")
    static class Enabled {

        @Bean(initMethod = "start", destroyMethod = "stop")
        RetentionScheduler retentionScheduler(ObjectProvider<RetrievalTracker> tracker,
                @Value("${casehub.rag.tracking.retention.days:90}") int retentionDays) {
            return new RetentionScheduler(required(tracker), retentionDays);
        }

        @Bean
        static BeanPostProcessor trackingCaseRetrieverPostProcessor(ObjectProvider<RetrievalTracker> tracker,
                ObjectProvider<ApplicationEventPublisher> events) {
            return new BeanPostProcessor() {
                @Override
                public Object postProcessAfterInitialization(Object bean, String beanName) {
                    if (!(bean instanceof CaseRetriever retriever) || bean instanceof TrackingCaseRetriever) {
                        return bean;
                    }
                    return new TrackingCaseRetriever(retriever, required(tracker), recorded -> {
                        ApplicationEventPublisher publisher = events.getIfAvailable();
                        if (publisher != null) {
                            publisher.publishEvent(recorded);
                        }
                    });
                }
            };
        }
    }

    private static RetrievalTracker required(ObjectProvider<RetrievalTracker> tracker) {
        RetrievalTracker current = tracker.getIfAvailable();
        if (current == null) {
            throw new IllegalStateException(ENABLED_PROPERTY + "=true needs a RetrievalTracker bean — set "
                    + PATH_PROPERTY + " for the SQLite tracker, or define one");
        }
        return current;
    }
}

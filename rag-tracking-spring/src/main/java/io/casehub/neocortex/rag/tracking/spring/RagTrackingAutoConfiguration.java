package io.casehub.neocortex.rag.tracking.spring;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.casehub.neocortex.rag.CaseRetriever;
import io.casehub.neocortex.rag.CorpusRef;
import io.casehub.neocortex.rag.PayloadFilter;
import io.casehub.neocortex.rag.RetrievalQuery;
import io.casehub.neocortex.rag.RetrievalTracker;
import io.casehub.neocortex.rag.tracking.RetentionScheduler;
import io.casehub.neocortex.rag.tracking.SqliteRetrievalTracker;
import io.casehub.neocortex.rag.tracking.TrackingCaseRetriever;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Arrays;
import org.aopalliance.intercept.MethodInterceptor;
import org.aopalliance.intercept.MethodInvocation;
import org.springframework.aop.framework.Advised;
import org.springframework.aop.framework.ProxyFactory;
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
import org.springframework.util.ClassUtils;

/**
 * Spring Boot wiring for neocortex retrieval tracking, with the Quarkus module's property names.
 *
 * <ul>
 *   <li>{@value #PATH_PROPERTY} set and no other {@link RetrievalTracker} bean: a
 *       {@link SqliteRetrievalTracker} bean (pool and busy timeout from
 *       {@code casehub.rag.tracking.sqlite.pool.max-size} / {@code …busy-timeout-ms}).</li>
 *   <li>{@value #ENABLED_PROPERTY}{@code =true}: every {@link CaseRetriever} bean is proxied (keeping its
 *       own class, so injection by concrete type still works) so that {@code retrieve} goes through a
 *       {@link TrackingCaseRetriever} publishing {@code RetrievalRecorded} application events, and a
 *       {@link RetentionScheduler} purges records older than {@code casehub.rag.tracking.retention.days}
 *       (default 90). Both need a {@code RetrievalTracker} bean.</li>
 * </ul>
 *
 * <p>Unlike the Quarkus decorator, which wraps only the injected outermost retriever, every
 * {@code CaseRetriever} bean is tracked. When one such bean delegates to another, the inner one records
 * first and the outer one sees its {@code _trackingId} and does not record again — so the record holds
 * the inner bean's results.
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
                    if (!(bean instanceof CaseRetriever retriever) || bean instanceof TrackingCaseRetriever
                            || isTracked(bean)) {
                        return bean;
                    }
                    TrackingCaseRetriever tracking = new TrackingCaseRetriever(retriever, required(tracker), recorded -> {
                        ApplicationEventPublisher publisher = events.getIfAvailable();
                        if (publisher != null) {
                            publisher.publishEvent(recorded);
                        }
                    });
                    return trackingProxy(bean, tracking);
                }
            };
        }
    }

    /**
     * A proxy of the bean's own class (CGLIB, falling back to its interfaces for final classes such as
     * lambdas) whose {@code retrieve} goes through tracking — so injection by the concrete type (for
     * example {@code HybridCaseRetriever}) keeps working, as with the Quarkus decorator.
     */
    static Object trackingProxy(Object bean, TrackingCaseRetriever tracking) {
        ProxyFactory factory = new ProxyFactory(bean);
        factory.setProxyTargetClass(!Modifier.isFinal(bean.getClass().getModifiers()));
        if (!factory.isProxyTargetClass()) {
            factory.setInterfaces(ClassUtils.getAllInterfaces(bean));
        }
        factory.addAdvice(new TrackingInterceptor(tracking));
        return factory.getProxy(bean.getClass().getClassLoader());
    }

    private static boolean isTracked(Object bean) {
        return bean instanceof Advised advised && Arrays.stream(advised.getAdvisors())
                .anyMatch(a -> a.getAdvice() instanceof TrackingInterceptor);
    }

    /** Routes {@code CaseRetriever.retrieve} through tracking; every other method goes to the bean. */
    private record TrackingInterceptor(TrackingCaseRetriever tracking) implements MethodInterceptor {
        @Override
        public Object invoke(MethodInvocation invocation) throws Throwable {
            Method method = invocation.getMethod();
            Object[] args = invocation.getArguments();
            if (method.getName().equals("retrieve") && args.length == 4
                    && Arrays.equals(method.getParameterTypes(), RETRIEVE.getParameterTypes())) {
                return tracking.retrieve((RetrievalQuery) args[0], (CorpusRef) args[1], (Integer) args[2],
                        (PayloadFilter) args[3]);
            }
            return invocation.proceed();
        }
    }

    private static final Method RETRIEVE;

    static {
        try {
            RETRIEVE = CaseRetriever.class.getMethod("retrieve", RetrievalQuery.class, CorpusRef.class, int.class,
                    PayloadFilter.class);
        } catch (NoSuchMethodException e) {
            throw new ExceptionInInitializerError(e);
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

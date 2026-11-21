package io.casehub.neocortex.rag.tracking.quarkus;

import io.casehub.neocortex.rag.RetrievalTracker;
import io.casehub.neocortex.rag.tracking.RetentionScheduler;
import io.quarkus.arc.properties.IfBuildProperty;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;

/** Runs the core {@link RetentionScheduler} for the bean's lifetime. */
@ApplicationScoped
@IfBuildProperty(name = "casehub.rag.tracking.enabled", stringValue = "true")
public class RetentionSchedulerBean {

    @Inject
    RetrievalTracker tracker;

    @ConfigProperty(name = "casehub.rag.tracking.retention.days", defaultValue = "90")
    int retentionDays;

    private RetentionScheduler scheduler;

    @PostConstruct
    void start() {
        scheduler = new RetentionScheduler(tracker, retentionDays);
        scheduler.start();
    }

    @PreDestroy
    void stop() {
        if (scheduler != null) scheduler.stop();
    }
}

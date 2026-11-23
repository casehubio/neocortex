package io.casehub.neocortex.rag.tracking.quarkus;

import static org.assertj.core.api.Assertions.assertThat;

import io.quarkus.arc.Arc;
import io.quarkus.arc.InjectableBean;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.enterprise.context.ApplicationScoped;
import org.junit.jupiter.api.Test;

@QuarkusTest
class RetentionSchedulerBeanTest {

    @Test
    void retentionSchedulerStartsWithTheApplication() {
        // Nothing injects the bean, so only an eager start creates it; looking it up here would create it.
        InjectableBean<RetentionSchedulerBean> bean = Arc.container().bean(
                Arc.container().beanManager().getBeans(RetentionSchedulerBean.class).stream()
                        .findFirst().map(b -> ((InjectableBean<?>) b).getIdentifier()).orElse("absent"));
        assertThat(bean).as("RetentionSchedulerBean is a bean").isNotNull();
        assertThat(Arc.container().getActiveContext(ApplicationScoped.class).get(bean))
                .as("created at startup, so the purge is scheduled").isNotNull();
    }
}

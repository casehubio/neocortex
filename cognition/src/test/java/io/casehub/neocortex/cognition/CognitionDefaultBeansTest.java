package io.casehub.neocortex.cognition;

import io.casehub.neocortex.cognition.appraisal.NoOpSalienceStrategy;
import io.casehub.neocortex.cognition.appraisal.SalienceStrategy;
import io.casehub.neocortex.cognition.salience.ConcernBasedSalienceStrategy;
import jakarta.enterprise.inject.Instance;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class CognitionDefaultBeansTest {

    @SuppressWarnings("unchecked")
    private static <T> Instance<T> resolvable() {
        Instance<T> instance = mock(Instance.class);
        when(instance.isResolvable()).thenReturn(true);
        when(instance.get()).thenReturn(null);
        return instance;
    }

    @SuppressWarnings("unchecked")
    private static <T> Instance<T> resolvable(T value) {
        Instance<T> instance = mock(Instance.class);
        when(instance.isResolvable()).thenReturn(true);
        when(instance.get()).thenReturn(value);
        return instance;
    }

    @SuppressWarnings("unchecked")
    private static <T> Instance<T> unresolvable() {
        Instance<T> instance = mock(Instance.class);
        when(instance.isResolvable()).thenReturn(false);
        return instance;
    }

    @Test
    void salienceStrategyReturnsConcernBasedWhenMindMapStoreAvailable() {
        var              beans    = new CognitionDefaultBeans();
        SalienceStrategy strategy = beans.salienceStrategy(resolvable(), unresolvable());
        assertThat(strategy).isInstanceOf(ConcernBasedSalienceStrategy.class);
    }

    @Test
    void salienceStrategyReturnsNoOpWhenMindMapStoreUnavailable() {
        var              beans    = new CognitionDefaultBeans();
        SalienceStrategy strategy = beans.salienceStrategy(unresolvable(), unresolvable());
        assertThat(strategy).isInstanceOf(NoOpSalienceStrategy.class);
    }

    @Test
    void salienceStrategyWrapsWithEntityAwareWhenAnnotationIndexAvailable() {
        var              beans    = new CognitionDefaultBeans();
        var              index    = new io.casehub.neocortex.annotation.inmem.InMemoryEntityAnnotationIndex();
        SalienceStrategy strategy = beans.salienceStrategy(resolvable(), resolvable(index));
        assertThat(strategy).isInstanceOf(io.casehub.neocortex.cognition.salience.EntityAwareSalienceStrategy.class);
    }
}

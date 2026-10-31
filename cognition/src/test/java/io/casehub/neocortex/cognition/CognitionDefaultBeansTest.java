package io.casehub.neocortex.cognition;

import io.casehub.neocortex.cognition.appraisal.NoOpSalienceStrategy;
import io.casehub.neocortex.cognition.appraisal.SalienceStrategy;
import io.casehub.neocortex.cognition.salience.ConcernBasedSalienceStrategy;
import io.casehub.neocortex.mindmap.MindMapStore;
import jakarta.enterprise.inject.Instance;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class CognitionDefaultBeansTest {

    @SuppressWarnings("unchecked")
    private static Instance<MindMapStore> resolvableInstance() {
        Instance<MindMapStore> instance = mock(Instance.class);
        when(instance.isResolvable()).thenReturn(true);
        return instance;
    }

    @SuppressWarnings("unchecked")
    private static Instance<MindMapStore> unresolvableInstance() {
        Instance<MindMapStore> instance = mock(Instance.class);
        when(instance.isResolvable()).thenReturn(false);
        return instance;
    }

    @Test
    void salienceStrategyReturnsConcernBasedWhenMindMapStoreAvailable() {
        var beans = new CognitionDefaultBeans();
        SalienceStrategy strategy = beans.salienceStrategy(resolvableInstance());
        assertThat(strategy).isInstanceOf(ConcernBasedSalienceStrategy.class);
    }

    @Test
    void salienceStrategyReturnsNoOpWhenMindMapStoreUnavailable() {
        var beans = new CognitionDefaultBeans();
        SalienceStrategy strategy = beans.salienceStrategy(unresolvableInstance());
        assertThat(strategy).isInstanceOf(NoOpSalienceStrategy.class);
    }
}

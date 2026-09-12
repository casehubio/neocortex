package io.casehub.neocortex.mindmap.spring;

import io.casehub.neocortex.mindmap.MindMapStore;
import io.casehub.neocortex.mindmap.runtime.NodeRefCleanupProcessor;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.context.annotation.Bean;

@AutoConfiguration
@ConditionalOnClass(NodeRefCleanupProcessor.class)
public class MindMapAutoConfiguration {

    @Bean
    public NodeRefCleanupProcessor nodeRefCleanupProcessor(MindMapStore store) {
        return new NodeRefCleanupProcessor(store);
    }
}

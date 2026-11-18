package io.casehub.neocortex.cognition.memory;

import jakarta.enterprise.context.ApplicationScoped;
import java.util.concurrent.atomic.AtomicReference;

@ApplicationScoped
public class AdaptiveRetentionConfig implements RetentionConfigSupplier {
    private final AtomicReference<RetentionConfig> config =
            new AtomicReference<>(RetentionConfig.DEFAULT);

    @Override
    public RetentionConfig current() {
        return config.get();
    }

    public void update(RetentionConfig newConfig) {
        config.set(newConfig);
    }
}

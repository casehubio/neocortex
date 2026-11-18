package io.casehub.neocortex.cognitive.index;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Instance;
import jakarta.enterprise.inject.Produces;
import jakarta.inject.Singleton;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * Makes {@link EffectiveCognitiveDefaults} available for injection.
 *
 * <p>{@code EffectiveCognitiveDefaults} is a plain class with no bean-defining annotation, so
 * beans that inject it directly (rather than through {@code Instance<>}) need a producer.
 * Both collaborating SPIs are optional: when no {@link DescriptorViewSource} bean is present
 * (for example {@code cognitive-eidos} is not on the classpath) descriptors come from the
 * explicit profiles in {@link CognitiveDefaultsRegistry}; when no {@link PersonalityStateSource}
 * bean is present {@link DefaultPersonalityStateSource} is used.
 */
@ApplicationScoped
public class EffectiveCognitiveDefaultsProducer {

    @Produces
    @Singleton
    EffectiveCognitiveDefaults effectiveCognitiveDefaults(CognitiveDefaultsRegistry registry,
                                                          Instance<DescriptorViewSource> viewSource,
                                                          Instance<PersonalityStateSource> stateSource) {
        return create(registry,
                viewSource.isResolvable() ? viewSource.get() : null,
                stateSource.isResolvable() ? stateSource.get() : null);
    }

    /** Framework-neutral factory; {@code viewSource} and {@code stateSource} may be {@code null}. */
    public static EffectiveCognitiveDefaults create(CognitiveDefaultsRegistry registry,
                                                    DescriptorViewSource viewSource,
                                                    PersonalityStateSource stateSource) {
        return new EffectiveCognitiveDefaults(registry,
                viewSource != null ? viewSource : registryBacked(registry),
                stateSource != null ? stateSource : new DefaultPersonalityStateSource());
    }

    private static DescriptorViewSource registryBacked(CognitiveDefaultsRegistry registry) {
        return new DescriptorViewSource() {
            @Override
            public Optional<DescriptorView> find(String tenantId, String agentId) {
                return registry.forAgent(agentId).map(CognitiveDefaults::descriptor);
            }

            @Override
            public List<String> agentIds(String tenantId) {
                return List.copyOf(registry.allAgentIds());
            }
        };
    }
}

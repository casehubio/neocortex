package io.casehub.neocortex.mindmap.intelligence.consolidation;

import io.casehub.neocortex.memory.experience.ExperienceRecorded;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.enterprise.inject.Instance;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.DoubleAdder;
import java.util.function.Consumer;
import java.util.logging.Level;
import java.util.logging.Logger;

@ApplicationScoped
public class SignificanceAccumulator {

    private static final Logger LOG = Logger.getLogger(
        SignificanceAccumulator.class.getName());

    private volatile ConcurrentHashMap<String, DoubleAdder> perTenant =
        new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Boolean> triggered = new ConcurrentHashMap<>();
    private final SignificanceExtractor extractor;
    private final double threshold;
    private final Consumer<String> triggerAction;
    private final boolean async;
    private final java.util.concurrent.ExecutorService triggerExecutor;

    @Inject
    SignificanceAccumulator(Instance<SignificanceExtractor> extractor,
                            Instance<ConsolidationScheduler> scheduler,
                            @ConfigProperty(
                                name = "casehub.consolidation.significance-threshold",
                                defaultValue = "10.0") double threshold) {
        this(extractor.isResolvable() ? extractor.get() : e -> 1.0,
             threshold,
             scheduler.isResolvable()
                 ? tenantId -> scheduler.get().consolidateNow(tenantId)
                 : tenantId -> {},
             true);
    }

    SignificanceAccumulator(SignificanceExtractor extractor,
                            double threshold,
                            Consumer<String> triggerAction) {
        this(extractor, threshold, triggerAction, false);
    }

    SignificanceAccumulator(SignificanceExtractor extractor,
                            double threshold,
                            Consumer<String> triggerAction,
                            boolean async) {
        this.extractor = extractor;
        this.threshold = threshold;
        this.triggerAction = triggerAction;
        this.async = async;
        this.triggerExecutor = async
            ? Executors.newSingleThreadExecutor(r -> {
                Thread t = new Thread(r, "significance-trigger");
                t.setDaemon(true);
                return t;
              })
            : null;
    }

    void onExperienceRecorded(@Observes ExperienceRecorded event) {
        double significance = extractor.extract(event);
        String tenantId = event.event().tenantId();
        DoubleAdder adder = perTenant.computeIfAbsent(
            tenantId, k -> new DoubleAdder());
        adder.add(significance);
        if (adder.sum() >= threshold && triggered.putIfAbsent(tenantId, Boolean.TRUE) == null) {
            if (triggerExecutor != null) {
                triggerExecutor.submit(() -> {
                    try {
                        triggerAction.accept(tenantId);
                    } catch (Exception e) {
                        LOG.log(Level.WARNING,
                            "Significance-triggered consolidation failed for "
                            + tenantId, e);
                    }
                });
            } else {
                triggerAction.accept(tenantId);
            }
        }
    }

    public SignificanceSnapshot swapAndReset() {
        var old          = perTenant;
        var oldTriggered = triggered;
        perTenant = new ConcurrentHashMap<>();
        oldTriggered.clear();
        var snapshot = new HashMap<String, Double>();
        old.forEach((k, v) -> snapshot.put(k, v.sum()));
        return new SignificanceSnapshot(Map.copyOf(snapshot));
    }
}

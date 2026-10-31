package io.casehub.neocortex.cognition.salience;

import io.casehub.neocortex.annotation.EntityAnnotation;
import io.casehub.neocortex.annotation.EntityAnnotationIndex;
import io.casehub.neocortex.cognition.appraisal.PerceivedSituation;
import io.casehub.neocortex.cognition.appraisal.SalienceContext;
import io.casehub.neocortex.cognition.appraisal.SalienceStrategy;
import io.casehub.neocortex.memory.Memory;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public class EntityAwareSalienceStrategy implements SalienceStrategy {

    private static final double ENTITY_BOOST = 0.2;

    private final SalienceStrategy      delegate;
    private final EntityAnnotationIndex annotationIndex;

    public EntityAwareSalienceStrategy(SalienceStrategy delegate,
                                       EntityAnnotationIndex annotationIndex) {
        this.delegate        = delegate;
        this.annotationIndex = annotationIndex;
    }

    @Override
    public PerceivedSituation perceive(SalienceContext context) {
        PerceivedSituation base = delegate.perceive(context);

        String tenantId = context.tenantId();
        if (tenantId == null) {return base;}

        Map<String, Double> boosted  = new HashMap<>(base.salience());
        String              obsLower = context.observation().toLowerCase(Locale.ROOT);

        for (Memory concern : context.activeConcerns()) {
            List<EntityAnnotation> annotations = annotationIndex.findBySource(
                    "memory", concern.memoryId(), tenantId);
            if (annotations.isEmpty()) {continue;}

            Set<String> entityNames = new HashSet<>();
            for (EntityAnnotation a : annotations) {
                entityNames.add(a.mentionText().toLowerCase(Locale.ROOT));
            }

            boolean matched = entityNames.stream().anyMatch(obsLower::contains);
            if (matched) {
                boosted.merge(concern.memoryId(), ENTITY_BOOST, Double::sum);
            }
        }

        return new PerceivedSituation(base.narrative(), boosted);
    }
}

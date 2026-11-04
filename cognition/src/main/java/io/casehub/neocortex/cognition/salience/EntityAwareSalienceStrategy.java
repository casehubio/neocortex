package io.casehub.neocortex.cognition.salience;

import io.casehub.neocortex.entity.token.ParsedToken;
import io.casehub.neocortex.entity.token.TokenFormat;
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

    private final SalienceStrategy delegate;

    public EntityAwareSalienceStrategy(SalienceStrategy delegate) {
        this.delegate = delegate;
    }

    @Override
    public PerceivedSituation perceive(SalienceContext context) {
        PerceivedSituation base = delegate.perceive(context);

        Map<String, Double> boosted  = new HashMap<>(base.salience());
        String              obsLower = context.observation().toLowerCase(Locale.ROOT);

        for (Memory concern : context.activeConcerns()) {
            List<ParsedToken> tokens = TokenFormat.parse(concern.text());
            if (tokens.isEmpty()) {continue;}

            Set<String> entityNames = new HashSet<>();
            for (ParsedToken t : tokens) {
                entityNames.add(t.surfaceForm().toLowerCase(Locale.ROOT));
            }

            boolean matched = entityNames.stream().anyMatch(obsLower::contains);
            if (matched) {
                boosted.merge(concern.memoryId(), ENTITY_BOOST, Double::sum);
            }
        }

        return new PerceivedSituation(base.narrative(), boosted);
    }
}

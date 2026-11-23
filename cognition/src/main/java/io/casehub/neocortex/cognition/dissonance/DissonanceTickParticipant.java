package io.casehub.neocortex.cognition.dissonance;

import io.casehub.neocortex.cognition.core.CognitionTickContext;
import io.casehub.neocortex.cognition.core.CognitionTickParticipant;
import io.casehub.neocortex.cognition.mood.MoodOrchestrator;
import io.casehub.neocortex.cognition.mood.MoodSignal;

import java.util.List;

public class DissonanceTickParticipant implements CognitionTickParticipant {

    private final DissonanceDetectionPhase phase;
    private final DissonanceDetectionConfig config;
    private final MoodOrchestrator moodOrchestrator;

    public DissonanceTickParticipant(DissonanceDetectionPhase phase,
                                     DissonanceDetectionConfig config,
                                     MoodOrchestrator moodOrchestrator) {
        this.phase = phase;
        this.config = config;
        this.moodOrchestrator = moodOrchestrator;
    }

    @Override
    public void tick(CognitionTickContext context) {
        var tensions = phase.activeTensions(context.agentId(), context.tenantId());
        if (tensions.isEmpty()) return;

        var deltas = computePadDeltas(tensions, config);
        moodOrchestrator.record(
            new MoodSignal.DirectShift(deltas.pleasure(), deltas.arousal(),
                deltas.dominance(), "dissonance-tension"),
            context.agentId(), context.tenantId());
    }

    record PadDelta(double arousal, double pleasure, double dominance) {}

    static PadDelta computePadDeltas(List<TensionSnapshot> tensions,
                                      DissonanceDetectionConfig config) {
        if (tensions.isEmpty()) return new PadDelta(0, 0, 0);

        double arousalSum = 0;
        double pleasureSum = 0;
        double maxStrength = 0;
        for (var t : tensions) {
            arousalSum += t.strength() * config.arousalWeight();
            pleasureSum += t.strength() * config.pleasureWeight();
            maxStrength = Math.max(maxStrength, t.strength());
        }

        double arousal = Math.min(config.arousalCap(), arousalSum);
        double pleasure = -Math.min(config.pleasureCap(), pleasureSum);
        double dominance = -Math.min(config.dominanceCap(),
            maxStrength * config.dominanceWeight());

        return new PadDelta(arousal, pleasure, dominance);
    }
}

package io.casehub.neocortex.cognition.subthought;

import io.casehub.neocortex.cognition.drive.DriveAxis;
import io.casehub.neocortex.memory.experience.SubThoughtTypes;

import java.util.EnumMap;
import java.util.Map;

public final class SubThoughtModulation {

    private static final double STEP = 0.15;

    private SubThoughtModulation() {}

    public static Map<DriveAxis, Double> compute(SubThoughtResult subThoughts) {
        if (subThoughts.isEmpty()) {
            return Map.of(
                    DriveAxis.AFFILIATION, 0.0, DriveAxis.COMPETENCE, 0.0,
                    DriveAxis.CURIOSITY, 0.0, DriveAxis.AUTONOMY, 0.0
                         );
        }

        double concern     = 0, affect = 0, intention = 0, evaluative = 0;
        double association = 0, causal = 0, selfReflection = 0;

        for (var st : subThoughts.subThoughts()) {
            switch (st.type()) {
                case SubThoughtTypes.CONCERN -> concern += st.confidence();
                case SubThoughtTypes.AFFECT_OBSERVATION -> affect += st.confidence();
                case SubThoughtTypes.INTENTION -> intention += st.confidence();
                case SubThoughtTypes.EVALUATIVE -> evaluative += st.confidence();
                case SubThoughtTypes.ASSOCIATION -> association += st.confidence();
                case SubThoughtTypes.CAUSAL_INFERENCE -> causal += st.confidence();
                case SubThoughtTypes.SELF_REFLECTION -> selfReflection += st.confidence();
                default -> {}
            }
        }

        var result = new EnumMap<DriveAxis, Double>(DriveAxis.class);
        result.put(DriveAxis.AFFILIATION, Math.min(1.0, (concern + affect) * STEP));
        result.put(DriveAxis.COMPETENCE, Math.min(1.0, (intention + evaluative) * STEP));
        result.put(DriveAxis.CURIOSITY, Math.min(1.0, (association + causal) * STEP));
        result.put(DriveAxis.AUTONOMY, Math.min(1.0, (intention + selfReflection) * STEP));
        return Map.copyOf(result);
    }
}

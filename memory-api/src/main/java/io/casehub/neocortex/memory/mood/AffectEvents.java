package io.casehub.neocortex.memory.mood;

import io.casehub.neocortex.cognitive.AffectType;
import io.casehub.neocortex.memory.MemoryDomain;
import io.casehub.neocortex.memory.MemoryInput;

import java.util.Map;

/**
 * Converts PAD changes on MindMap nodes into {@code domain="affect"} memory
 * entries. Each entry is a timestamped snapshot of the node's emotional state.
 *
 * <p>Follows the same converter pattern as {@link MoodEvents} and
 * {@code ExperienceEvents}. The stored memories form a trajectory log —
 * queryable via {@code MemoryQuery.forEntity(nodeId, DOMAIN, tenantId)}.
 */
public final class AffectEvents {

    public static final MemoryDomain DOMAIN = new MemoryDomain("affect");

    private AffectEvents() {}

    public static MemoryInput toMemoryInput(String nodeId, String tenantId,
                                            double pleasure, double arousal, double dominance) {return toMemoryInput(nodeId, tenantId, pleasure, arousal, dominance, AffectType.INHERENT);}

    public static MemoryInput toMemoryInput(String nodeId, String tenantId,
                                            double pleasure, double arousal, double dominance,
                                            AffectType affectType) {
        return new MemoryInput(nodeId, DOMAIN, tenantId, null, "PAD update",
                               Map.of("affect-type", affectType.name().toLowerCase()),
                               null, pleasure, arousal, dominance);
    }

}

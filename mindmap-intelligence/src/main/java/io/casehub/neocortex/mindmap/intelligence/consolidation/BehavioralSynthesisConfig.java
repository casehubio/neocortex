package io.casehub.neocortex.mindmap.intelligence.consolidation;

import io.smallrye.config.ConfigMapping;
import io.smallrye.config.WithDefault;

/**
 * Configuration for behavioral attractor synthesis during consolidation.
 *
 * Literature grounding:
 * - EMA retention: Rescorla-Wagner α=0.3-0.4 for single trials (Rescorla & Wagner, 1972).
 *   Consolidation batches multiple experiences, justifying α=0.5 (faster convergence).
 * - Reinforcement boost: Power law of practice (Newell & Rosenbloom, 1981) — logarithmic
 *   scaling produces diminishing returns consistent with progressive chunking.
 * - Boost cap 0.5: Limits reinforcement boost to 50% above settler output, preventing
 *   runaway strength from repetitive but low-quality experiences.
 */
@ConfigMapping(prefix = "casehub.mindmap.consolidation.behavioral")
public interface BehavioralSynthesisConfig {

    @WithDefault("0.5")
    double emaRetention();

    @WithDefault("0.5")
    double reinforcementBoostCap();

    @WithDefault("20")
    int reinforcementScale();

    @WithDefault("20")
    int maxPerPass();
}

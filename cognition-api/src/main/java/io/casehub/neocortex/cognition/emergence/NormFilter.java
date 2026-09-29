package io.casehub.neocortex.cognition.emergence;

import java.util.List;

@FunctionalInterface
public interface NormFilter {
    List<SocialNorm> filter(List<SocialNorm> norms, String agentId, String tenantId);
}

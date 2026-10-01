package io.casehub.neocortex.mindmap;

import java.util.List;

public interface RuleResolver {

    List<TraitRule> traitRules(String agentId);

    List<TraitRule> allTraitRules();

    List<DerivedEdgeRule> derivedEdgeRules(String agentId);

    List<DerivedEdgeRule> allDerivedEdgeRules();
}

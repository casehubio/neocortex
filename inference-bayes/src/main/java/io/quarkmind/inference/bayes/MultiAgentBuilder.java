package io.quarkmind.inference.bayes;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class MultiAgentBuilder {

    private final String name;
    private JunctionTree sharedTree;
    private final List<AgentDefinition> agents = new ArrayList<>();
    private final List<LinkDefinition> links = new ArrayList<>();
    private final Set<String> commonKnowledgeVars = new LinkedHashSet<>();
    private AgentDefinition currentAgent;

    private MultiAgentBuilder(String name) {
        this.name = name;
    }

    public static MultiAgentBuilder create(String name) {
        return new MultiAgentBuilder(name);
    }

    public MultiAgentBuilder sharedNetwork(JunctionTree tree) {
        this.sharedTree = tree;
        return this;
    }

    public AgentDef agent(String agentName) {
        finalizeCurrentAgent();
        currentAgent = new AgentDefinition(agentName, null);
        return new AgentDef(this);
    }

    public AgentDef agent(String agentName, JunctionTree tree) {
        finalizeCurrentAgent();
        currentAgent = new AgentDefinition(agentName, tree);
        return new AgentDef(this);
    }

    public LinkSourceDef link(String sourceAgent, String sourceVar) {
        finalizeCurrentAgent();
        return new LinkSourceDef(this, sourceAgent, sourceVar);
    }

    public MultiAgentBuilder commonKnowledge(String variableName) {
        finalizeCurrentAgent();
        commonKnowledgeVars.add(variableName);
        return this;
    }

    public MultiAgentComposition build() {
        finalizeCurrentAgent();
        validate();
        NetworkComposition composition = buildComposition();
        return new MultiAgentComposition(name, composition,
            agents.stream().map(a -> a.name).toList(), commonKnowledgeVars);
    }

    private void finalizeCurrentAgent() {
        if (currentAgent != null) {
            agents.add(currentAgent);
            currentAgent = null;
        }
    }

    private void validate() {
        if (agents.size() < 2) {
            throw new IllegalArgumentException(
                "Multi-agent composition requires at least 2 agents, got " + agents.size());
        }
        Set<String> names = new HashSet<>();
        for (var agent : agents) {
            if (!names.add(agent.name)) {
                throw new IllegalArgumentException("Duplicate agent name: " + agent.name);
            }
        }
        for (var link : links) {
            if (!names.contains(link.sourceAgent)) {
                throw new IllegalArgumentException(
                    "Link references unknown source agent: " + link.sourceAgent);
            }
            if (!names.contains(link.targetAgent)) {
                throw new IllegalArgumentException(
                    "Link references unknown target agent: " + link.targetAgent);
            }
        }
        Set<String> linkTargets = new HashSet<>();
        for (var link : links) {
            linkTargets.add(link.targetAgent + "." + link.targetVar);
        }
        for (String ckVar : commonKnowledgeVars) {
            for (var agent : agents) {
                JunctionTree tree = agent.tree != null ? agent.tree : sharedTree;
                if (tree == null) {
                    throw new IllegalArgumentException(
                        "Agent '" + agent.name + "' has no tree — provide sharedNetwork() or per-agent tree");
                }
                boolean found = false;
                for (var node : tree.getGraph()) {
                    if (node.getContent().getName().equals(ckVar)) {
                        found = true;
                        break;
                    }
                }
                if (!found) {
                    throw new IllegalArgumentException(
                        "Common knowledge variable '" + ckVar + "' not found in agent '" + agent.name + "'");
                }
            }
            for (var agent : agents) {
                if (linkTargets.contains(agent.name + "." + ckVar)) {
                    throw new IllegalArgumentException(
                        "Common knowledge variable '" + ckVar + "' conflicts with link target on agent '"
                        + agent.name + "'");
                }
            }
        }
    }

    private NetworkComposition buildComposition() {
        List<NetworkSlot> slots = new ArrayList<>();
        for (var agent : agents) {
            JunctionTree tree = agent.tree != null ? agent.tree : sharedTree;
            slots.add(new NetworkSlot(agent.name, tree, Map.copyOf(agent.variants)));
        }
        List<NetworkLink> networkLinks = new ArrayList<>();
        for (var link : links) {
            networkLinks.add(new NetworkLink(link.sourceAgent, link.targetAgent,
                Map.of(link.sourceVar, link.targetVar), link.mode));
        }
        return new NetworkComposition(name, slots, networkLinks);
    }

    public static class AgentDef {
        private final MultiAgentBuilder builder;

        AgentDef(MultiAgentBuilder builder) {
            this.builder = builder;
        }

        public AgentDef variant(String contextKey, JunctionTree tree) {
            builder.currentAgent.variants.put(contextKey, tree);
            return this;
        }

        public AgentDef agent(String name) { return builder.agent(name); }
        public AgentDef agent(String name, JunctionTree tree) { return builder.agent(name, tree); }
        public LinkSourceDef link(String sourceAgent, String sourceVar) { return builder.link(sourceAgent, sourceVar); }
        public MultiAgentBuilder commonKnowledge(String variableName) { return builder.commonKnowledge(variableName); }
        public MultiAgentComposition build() { return builder.build(); }
    }

    public static class LinkSourceDef {
        private final MultiAgentBuilder builder;
        private final String sourceAgent;
        private final String sourceVar;

        LinkSourceDef(MultiAgentBuilder builder, String sourceAgent, String sourceVar) {
            this.builder = builder;
            this.sourceAgent = sourceAgent;
            this.sourceVar = sourceVar;
        }

        public LinkDef to(String targetAgent, String targetVar) {
            var def = new LinkDefinition(sourceAgent, targetAgent, sourceVar, targetVar);
            builder.links.add(def);
            return new LinkDef(builder, def);
        }

        public LinkDef to(String targetAgent) {
            return to(targetAgent, sourceVar);
        }
    }

    public static class LinkDef {
        private final MultiAgentBuilder builder;
        private final LinkDefinition def;

        LinkDef(MultiAgentBuilder builder, LinkDefinition def) {
            this.builder = builder;
            this.def = def;
        }

        public LinkDef mode(TransferMode mode) {
            def.mode = mode;
            return this;
        }

        public AgentDef agent(String name) { return builder.agent(name); }
        public AgentDef agent(String name, JunctionTree tree) { return builder.agent(name, tree); }
        public LinkSourceDef link(String sourceAgent, String sourceVar) { return builder.link(sourceAgent, sourceVar); }
        public MultiAgentBuilder commonKnowledge(String variableName) { return builder.commonKnowledge(variableName); }
        public MultiAgentComposition build() { return builder.build(); }
    }

    private static class AgentDefinition {
        final String name;
        final JunctionTree tree;
        final Map<String, JunctionTree> variants = new LinkedHashMap<>();

        AgentDefinition(String name, JunctionTree tree) {
            this.name = name;
            this.tree = tree;
        }
    }

    private static class LinkDefinition {
        final String sourceAgent;
        final String targetAgent;
        final String sourceVar;
        final String targetVar;
        TransferMode mode = TransferMode.POSTERIOR;

        LinkDefinition(String sourceAgent, String targetAgent, String sourceVar, String targetVar) {
            this.sourceAgent = sourceAgent;
            this.targetAgent = targetAgent;
            this.sourceVar = sourceVar;
            this.targetVar = targetVar;
        }
    }
}

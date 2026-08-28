package io.casehub.neocortex.mindmap;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class AbstractForwardingMindMapStoreTest {

    @Test
    void allMethodsDelegateToWrappedStore() {
        var calls = new java.util.ArrayList<String>();
        MindMapStore delegate = new MindMapStore() {
            @Override public void registerVocabulary(MindMapVocabulary v) { calls.add("registerVocabulary"); }
            @Override public String addNode(NodeInput i, String t) { calls.add("addNode"); return "n1"; }
            @Override public MindMapNode getNode(String id, String t) { calls.add("getNode"); return null; }
            @Override public void updateNode(String id, NodeUpdate u, String t) { calls.add("updateNode"); }
            @Override public String addEdge(EdgeInput i, String t) { calls.add("addEdge"); return "e1"; }
            @Override public MindMapEdge getEdge(String id, String t) { calls.add("getEdge"); return null; }
            @Override public void removeEdge(String id, String t) { calls.add("removeEdge"); }
            @Override public void addAlias(String id, String a, String t) { calls.add("addAlias"); }
            @Override public void removeAlias(String id, String a, String t) { calls.add("removeAlias"); }
            @Override public MindMapNode resolveNode(String n, String s, String t) { calls.add("resolveNode"); return null; }
            @Override public MergeResult mergeNodes(String k, String r, String t) { calls.add("mergeNodes"); return null; }
            @Override public String createSubgraph(SubgraphInput i, String t) { calls.add("createSubgraph"); return "sg1"; }
            @Override public MindMapSubgraph getSubgraph(String id, String t) { calls.add("getSubgraph"); return null; }
            @Override public void updateSubgraph(String id, String r, String t) { calls.add("updateSubgraph"); }
            @Override public List<MindMapSubgraph> listSubgraphs(String t) { calls.add("listSubgraphs"); return List.of(); }
            @Override public List<MindMapNode> nodesIn(String s, String t) { calls.add("nodesIn"); return List.of(); }
            @Override public List<MindMapEdge> bridgeEdges(String s, String t) { calls.add("bridgeEdges"); return List.of(); }
            @Override public List<MindMapEdge> neighbors(String id, String t) { calls.add("neighbors"); return List.of(); }
            @Override public List<MindMapEdge> neighbors(String id, String e, String t) { calls.add("neighborsTyped"); return List.of(); }
            @Override public List<MindMapNode> search(MindMapQuery q) { calls.add("search"); return List.of(); }
            @Override public void supersede(String t, String s, String r, String tid) { calls.add("supersede"); }
            @Override public void reinstate(String id, String t) { calls.add("reinstate"); }
            @Override public SupersessionStatus getSupersessionStatus(String id, String t) { calls.add("getSupersessionStatus"); return null; }
            @Override public int eraseNode(String id, String t) { calls.add("eraseNode"); return 1; }
            @Override public int eraseSubgraph(String id, String t) { calls.add("eraseSubgraph"); return 1; }
            @Override public int eraseEntity(String n, String t) { calls.add("eraseEntity"); return 1; }
            @Override public int eraseEntityAcrossTenants(String n, Set<String> t) { calls.add("eraseEntityAcrossTenants"); return 1; }
            @Override public Set<MindMapCapability> capabilities() { calls.add("capabilities"); return Set.of(); }
        };

        var forwarding = new AbstractForwardingMindMapStore(delegate) {};

        forwarding.registerVocabulary(null);
        forwarding.addNode(null, "t");
        forwarding.getNode("n", "t");
        forwarding.updateNode("n", null, "t");
        forwarding.addEdge(null, "t");
        forwarding.getEdge("e", "t");
        forwarding.removeEdge("e", "t");
        forwarding.addAlias("n", "a", "t");
        forwarding.removeAlias("n", "a", "t");
        forwarding.resolveNode("n", null, "t");
        forwarding.mergeNodes("k", "r", "t");
        forwarding.createSubgraph(null, "t");
        forwarding.getSubgraph("sg", "t");
        forwarding.updateSubgraph("sg", "r", "t");
        forwarding.listSubgraphs("t");
        forwarding.nodesIn("sg", "t");
        forwarding.bridgeEdges("sg", "t");
        forwarding.neighbors("n", "t");
        forwarding.neighbors("n", "type", "t");
        forwarding.search(null);
        forwarding.supersede("t", "s", "r", "tid");
        forwarding.reinstate("n", "t");
        forwarding.getSupersessionStatus("n", "t");
        forwarding.eraseNode("n", "t");
        forwarding.eraseSubgraph("sg", "t");
        forwarding.eraseEntity("e", "t");
        forwarding.eraseEntityAcrossTenants("e", Set.of());
        forwarding.capabilities();

        assertThat(calls).containsExactly(
            "registerVocabulary", "addNode", "getNode", "updateNode",
            "addEdge", "getEdge", "removeEdge",
            "addAlias", "removeAlias", "resolveNode", "mergeNodes",
            "createSubgraph", "getSubgraph", "updateSubgraph", "listSubgraphs",
            "nodesIn", "bridgeEdges", "neighbors", "neighborsTyped", "search",
            "supersede", "reinstate", "getSupersessionStatus",
            "eraseNode", "eraseSubgraph", "eraseEntity", "eraseEntityAcrossTenants",
            "capabilities");
    }

    @Test
    void delegateAccessible() {
        MindMapStore delegate = new MindMapStore() {
            @Override
            public void registerVocabulary(MindMapVocabulary v)                  {}

            @Override
            public String addNode(NodeInput i, String t)                         {return null;}

            @Override
            public MindMapNode getNode(String id, String t)                      {return null;}

            @Override
            public void updateNode(String id, NodeUpdate u, String t)            {}

            @Override
            public String addEdge(EdgeInput i, String t)                         {return null;}

            @Override
            public MindMapEdge getEdge(String id, String t)                      {return null;}

            @Override
            public void removeEdge(String id, String t)                          {}

            @Override
            public void addAlias(String id, String a, String t)                  {}

            @Override
            public void removeAlias(String id, String a, String t)               {}

            @Override
            public MindMapNode resolveNode(String n, String s, String t)         {return null;}

            @Override
            public MergeResult mergeNodes(String k, String r, String t)          {return null;}

            @Override
            public String createSubgraph(SubgraphInput i, String t)              {return null;}

            @Override
            public MindMapSubgraph getSubgraph(String id, String t)              {return null;}

            @Override
            public void updateSubgraph(String id, String r, String t)            {}

            @Override
            public List<MindMapSubgraph> listSubgraphs(String t)                 {return List.of();}

            @Override
            public List<MindMapNode> nodesIn(String s, String t)                 {return List.of();}

            @Override
            public List<MindMapEdge> bridgeEdges(String s, String t)             {return List.of();}

            @Override
            public List<MindMapEdge> neighbors(String id, String t)              {return List.of();}

            @Override
            public List<MindMapEdge> neighbors(String id, String e, String t)    {return List.of();}

            @Override
            public List<MindMapNode> search(MindMapQuery q)                      {return List.of();}

            @Override
            public void supersede(String t, String s, String r, String tid)      {}

            @Override
            public void reinstate(String id, String t)                           {}

            @Override
            public SupersessionStatus getSupersessionStatus(String id, String t) {return null;}

            @Override
            public int eraseNode(String id, String t)                            {return 0;}

            @Override
            public int eraseSubgraph(String id, String t)                        {return 0;}

            @Override
            public int eraseEntity(String n, String t)                           {return 0;}

            @Override
            public int eraseEntityAcrossTenants(String n, Set<String> t)         {return 0;}

            @Override
            public Set<MindMapCapability> capabilities()                         {return Set.of();}
        };
        var forwarding = new AbstractForwardingMindMapStore(delegate) {};
        assertThat(forwarding.delegate()).isSameAs(delegate);
    }
}

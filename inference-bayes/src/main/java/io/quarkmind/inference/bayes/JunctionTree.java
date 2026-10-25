/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package io.quarkmind.inference.bayes;

import io.quarkmind.inference.bayes.graph.Graph;
import io.quarkmind.inference.bayes.graph.GraphNode;

import java.util.List;

public class JunctionTree {
    private Graph<BayesVariable>    graph;
    private JunctionTreeClique      root;
    private JunctionTreeClique[]    jtNodes;
    private JunctionTreeSeparator[] jtSeps;
    private boolean                 frozen;


    public JunctionTree(Graph<BayesVariable> graph, JunctionTreeClique root, JunctionTreeClique[] jtNodes, JunctionTreeSeparator[] jtSeps) {
        this( graph, root, jtNodes, jtSeps, true );
    }


    public JunctionTree(Graph<BayesVariable> graph, JunctionTreeClique root, JunctionTreeClique[] jtNodes, JunctionTreeSeparator[] jtSeps, boolean init) {
        this.graph = graph;
        this.root = root;
        this.jtNodes = jtNodes;
        this.jtSeps = jtSeps;
        if ( init ) {
            initialize();
        }
    }

    public Graph<BayesVariable> getGraph() {
        return graph;
    }

    public JunctionTreeClique getRoot() {
        return root;
    }

    private void initialize() {
        recurseJTNodesAndInitialisePotentials( graph, root );
    }


    public void recurseJTNodesAndInitialisePotentials(Graph graph, JunctionTreeClique jtNode) {
        BayesVariable[] vars = jtNode.getValues().toArray( new BayesVariable[jtNode.getValues().size()] );

        List<BayesVariable> family = jtNode.getFamily();
        int numberOfStates = PotentialMultiplier.createNumberOfStates(vars);
        int[] multipliers = PotentialMultiplier.createIndexMultipliers(vars, numberOfStates);
        for ( BayesVariable var : family ) {
            multipleVarNodePotential(graph.getNode( var.getId() ), jtNode.getPotentials(), vars, multipliers);
        }

        List<JunctionTreeSeparator> seps = jtNode.getChildren();
        for ( JunctionTreeSeparator sep : seps ) {
            recurseJTNodesAndInitialisePotentials(graph, sep.getChild());
        }
    }

    public void multipleVarNodePotential(GraphNode<BayesVariable> varNode, double[] potentials, BayesVariable[] vars, int[] multipliers) {
        BayesVariable[] parents = new BayesVariable[varNode.getInEdges().size()];
        for (int i = 0; i < parents.length; i++) {
            parents[i] = (BayesVariable) varNode.getInEdges().get(i).getOutGraphNode().getContent();
        }

        int[] parentVarPos           = PotentialMultiplier.createSubsetVarPos(vars, parents);
        int   parentsNumberOfStates  = PotentialMultiplier.createNumberOfStates(parents);
        int[] parentIndexMultipliers = PotentialMultiplier.createIndexMultipliers(parents, parentsNumberOfStates);

        BayesVariable var    = varNode.getContent();
        int           varPos = -1;
        for (int i = 0; i < vars.length; i++) {
            if (vars[i] == var) {
                varPos = i;
                break;
            }
        }
        if (varPos == -1 || varPos == vars.length) {
            throw new IllegalStateException("Unable to find Variable in set");
        }

        double[][] logCpt = new double[var.getProbabilityTable().length][];
        for (int i = 0; i < logCpt.length; i++) {
            logCpt[i] = LogSpace.toLogSpaceCopy(var.getProbabilityTable()[i]);
        }

        PotentialMultiplier m = new PotentialMultiplier(logCpt, varPos, parentVarPos, parentIndexMultipliers,
                                                        vars, multipliers, potentials);
        m.multiple();
    }

    public JunctionTreeClique[] getJunctionTreeNodes() {
        return jtNodes;
    }

    public JunctionTreeSeparator[] getJunctionTreeSeparators() {
        return jtSeps;
    }

    public void setJunctionTreeNodes(JunctionTreeClique[] jtNodes) {
        if (frozen) {
            throw new IllegalStateException("Cannot modify a frozen JunctionTree");
        }
        this.jtNodes = jtNodes;
    }

    public void setJunctionTreeSeparators(JunctionTreeSeparator[] jtSeps) {
        if (frozen) {
            throw new IllegalStateException("Cannot modify a frozen JunctionTree");
        }
        this.jtSeps = jtSeps;
    }

    public JunctionTree freeze() {
        this.frozen = true;
        return this;
    }

    public boolean isFrozen() {
        return frozen;
    }

    public JunctionTree defensiveCopy() {
        JunctionTreeClique[]    nodesCopy = java.util.Arrays.copyOf(jtNodes, jtNodes.length);
        JunctionTreeSeparator[] sepsCopy  = java.util.Arrays.copyOf(jtSeps, jtSeps.length);
        return new JunctionTree(graph, root, nodesCopy, sepsCopy, false);
    }


}

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

import io.quarkmind.inference.bayes.bitmask.BitMask;
import io.quarkmind.inference.bayes.graph.Graph;
import io.quarkmind.inference.bayes.graph.GraphNode;

import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class BayesInstance<T> {
    private Graph<BayesVariable>       graph;
    private JunctionTree               tree;
    private Map<String, BayesVariable> variables;
    private Map<String, BayesVariable> fieldNames;
    private BayesLikelyhood[]          likelyhoods;
    private BayesLikelyhood[]          previousLikelyhoods;
    private BitMask                    dirty;
    private BitMask                    decided;
    private boolean                    calibrated;

    private CliqueState[]        cliqueStates;
    private SeparatorState[]     separatorStates;
    private BayesVariableState[] varStates;

    // Phase 2: dirty subtree tracking + separator snapshots
    private boolean[] subtreeHasDirty;
    private double[][] sepPotSnapshots;

    // Phase 3: calibrated snapshots for fast evidence retraction
    private boolean enableFastRetract;
    private double[][] calibratedCliquePots;
    private double[][] calibratedSepPots;

    private GlobalUpdateListener globalUpdateListener;
    private PassMessageListener  passMessageListener;
    private int                  statDirtyCount;
    private long                 statLastPropNanos;
    private int                  statRetractionCount;


    public BayesInstance(JunctionTree tree, boolean enableFastRetract) {
        this(tree);
        this.enableFastRetract = enableFastRetract;
        if (enableFastRetract) {
            calibratedCliquePots = new double[tree.getJunctionTreeNodes().length][];
            for (JunctionTreeClique clique : tree.getJunctionTreeNodes()) {
                calibratedCliquePots[clique.getId()] =
                        new double[cliqueStates[clique.getId()].getPotentials().length];
            }
            calibratedSepPots = new double[tree.getJunctionTreeSeparators().length][];
            for (JunctionTreeSeparator sep : tree.getJunctionTreeSeparators()) {
                calibratedSepPots[sep.getId()] =
                        new double[separatorStates[sep.getId()].getPotentials().length];
            }
        }
    }

    public BayesInstance(JunctionTree tree) {
        this.graph = tree.getGraph();
        this.tree = tree;
        variables = new HashMap<>();
        fieldNames = new HashMap<>();
        likelyhoods = new BayesLikelyhood[graph.size()];
        previousLikelyhoods = new BayesLikelyhood[graph.size()];
        dirty = BitMask.getEmpty(graph.size());
        decided = BitMask.getEmpty(graph.size());
        this.enableFastRetract = false;

        cliqueStates = new CliqueState[tree.getJunctionTreeNodes().length];
        for (JunctionTreeClique clique : tree.getJunctionTreeNodes()) {
            cliqueStates[clique.getId()] = clique.createState();
        }

        separatorStates = new SeparatorState[tree.getJunctionTreeSeparators().length];
        for ( JunctionTreeSeparator sep : tree.getJunctionTreeSeparators() ) {
            separatorStates[sep.getId()] = sep.createState();
        }

        subtreeHasDirty = new boolean[tree.getJunctionTreeNodes().length];

        sepPotSnapshots = new double[tree.getJunctionTreeSeparators().length][];
        for (JunctionTreeSeparator sep : tree.getJunctionTreeSeparators()) {
            sepPotSnapshots[sep.getId()] = new double[separatorStates[sep.getId()].getPotentials().length];
        }

        varStates = new BayesVariableState[graph.size()];
        for (GraphNode<BayesVariable> node : graph) {
            BayesVariable var = node.getContent();
            variables.put(var.getName(), var);
            varStates[var.getId()] = var.createState();
        }
    }

    private void snapshotSeparators() {
        for (JunctionTreeSeparator sep : tree.getJunctionTreeSeparators()) {
            double[] current = separatorStates[sep.getId()].getPotentials();
            System.arraycopy(current, 0, sepPotSnapshots[sep.getId()], 0, current.length);
        }
    }

    private void saveCalibrated() {
        if (!enableFastRetract) return;
        for (JunctionTreeClique clique : tree.getJunctionTreeNodes()) {
            double[] src = cliqueStates[clique.getId()].getPotentials();
            System.arraycopy(src, 0, calibratedCliquePots[clique.getId()], 0, src.length);
        }
        for (JunctionTreeSeparator sep : tree.getJunctionTreeSeparators()) {
            double[] src = separatorStates[sep.getId()].getPotentials();
            System.arraycopy(src, 0, calibratedSepPots[sep.getId()], 0, src.length);
        }
    }

    private boolean isRetractable() {
        for (int varId = 0; varId < likelyhoods.length; varId++) {
            if (!dirty.isSet(varId)) {continue;}
            if (previousLikelyhoods[varId] == null) {return false;}
            BayesLikelyhood newL = likelyhoods[varId];
            if (newL != null) {
                for (double v : newL.getDistribution()) {
                    if (v == 0.0) {return false;}
                }
            }
            int cliqueId = varStates[varId].getVariable().getFamily();
            for (double v : calibratedCliquePots[cliqueId]) {
                if (v == LogSpace.LOG_ZERO) {return false;}
            }
        }
        for (JunctionTreeSeparator sep : tree.getJunctionTreeSeparators()) {
            if (subtreeHasDirty[sep.getChild().getId()]) {
                for (double v : calibratedSepPots[sep.getId()]) {
                    if (v == LogSpace.LOG_ZERO) {return false;}
                }
            }
        }
        return true;
    }

    private void retractionUpdate() {
        for (int varId = 0; varId < likelyhoods.length; varId++) {
            if (!dirty.isSet(varId)) continue;

            int cliqueId = varStates[varId].getVariable().getFamily();
            double[] cliquePots = cliqueStates[cliqueId].getPotentials();

            // Restore calibrated clique potentials as starting point
            System.arraycopy(calibratedCliquePots[cliqueId], 0, cliquePots, 0, cliquePots.length);

            // Divide out old evidence
            BayesLikelyhood old = previousLikelyhoods[varId];
            if (old != null) {
                old.divideFrom(cliquePots);
            }

            // Multiply in new evidence
            BayesLikelyhood newL = likelyhoods[varId];
            if (newL != null) {
                newL.multiplyInto(cliquePots);
            }

            BayesAbsorption.normalize(cliquePots);
        }

        // Incremental collect using calibrated separator state as the no-op reference
        // (do NOT modify sepPotSnapshots — it must retain its post-collect values for Phase 2 fallback)
        collectEvidenceRetract(tree.getRoot());

        // After retraction collect, update sepPotSnapshots only for dirty-path separators
        // (those that passMessage actually updated). Clean-path seps keep their existing
        // post-collect snapshot for use in subsequent Phase 2 calls.
        snapshotDirtyPathSeparators();

        distributeEvidence(tree.getRoot());

        // Clean up subtreeHasDirty after retractionUpdate is complete
        Arrays.fill(subtreeHasDirty, false);
    }

    /**
     * Incremental collect for the Phase 3 retraction path.
     * Uses calibratedSepPots (post-distribute) as the no-op reference for clean separators,
     * leaving sepPotSnapshots untouched.
     */
    private void collectEvidenceRetract(JunctionTreeClique startClique) {
        collectChildEvidenceRetract(startClique, startClique);
    }

    private void collectChildEvidenceRetract(JunctionTreeClique clique, JunctionTreeClique startClique) {
        List<JunctionTreeSeparator> seps = clique.getChildren();
        for (JunctionTreeSeparator sep : seps) {
            collectChildEvidenceRetract(sep.getChild(), startClique);
        }

        if (clique.getParentSeparator() != null && clique != startClique) {
            JunctionTreeSeparator parentSep = clique.getParentSeparator();
            JunctionTreeClique parent = parentSep.getParent();

            if (subtreeHasDirty[clique.getId()]) {
                passMessage(clique, parentSep, parent); // dirty: recompute
            } else {
                injectCalibratedAndAbsorb(parentSep, parent); // clean: inject calibrated (no-op)
            }
        }
    }

    /**
     * Like injectSnapshotAndAbsorb, but uses calibratedSepPots instead of sepPotSnapshots.
     * Since calibratedSepPots equals the current separator state (both post-distribute),
     * the absorption ratio is 1.0 — effectively a no-op.
     */
    private void injectCalibratedAndAbsorb(JunctionTreeSeparator sep, JunctionTreeClique targetClique) {
        double[] sepPots = separatorStates[sep.getId()].getPotentials();
        double[] oldSepPots = Arrays.copyOf(sepPots, sepPots.length);
        System.arraycopy(calibratedSepPots[sep.getId()], 0, sepPots, 0, sepPots.length);
        BayesVariable[] sepVars = sep.getValues().toArray(new BayesVariable[sep.getValues().size()]);
        absorb(sepVars, cliqueStates[targetClique.getId()], separatorStates[sep.getId()], oldSepPots);
    }

    private void snapshotDirtyPathSeparators() {
        for (JunctionTreeSeparator sep : tree.getJunctionTreeSeparators()) {
            // A separator is on the dirty path if the child clique has a dirty subtree
            // (i.e., passMessage was called for it during retraction collect).
            if (sep.getChild() != null && subtreeHasDirty[sep.getChild().getId()]) {
                double[] current = separatorStates[sep.getId()].getPotentials();
                System.arraycopy(current, 0, sepPotSnapshots[sep.getId()], 0, current.length);
            }
        }
    }

    private void computeDirtySubtrees() {
        Arrays.fill(subtreeHasDirty, false);
        for (int varId = 0; varId < likelyhoods.length; varId++) {
            if (dirty.isSet(varId)) {
                int cliqueId = varStates[varId].getVariable().getFamily();
                markSubtreeDirtyUpward(tree.getJunctionTreeNodes()[cliqueId]);
            }
        }
    }

    private void markSubtreeDirtyUpward(JunctionTreeClique clique) {
        if (subtreeHasDirty[clique.getId()]) {
            return; // already marked — ancestor path already processed
        }
        subtreeHasDirty[clique.getId()] = true;
        JunctionTreeSeparator parentSep = clique.getParentSeparator();
        if (parentSep != null) {
            markSubtreeDirtyUpward(parentSep.getParent());
        }
    }

    public void reset() {
        for (JunctionTreeClique clique : tree.getJunctionTreeNodes()) {
            clique.resetState(cliqueStates[clique.getId()]);
        }

        for ( JunctionTreeSeparator sep : tree.getJunctionTreeSeparators() ) {
            sep.resetState(separatorStates[sep.getId()]);
        }

        for (GraphNode<BayesVariable> node : graph) {
            BayesVariable var = node.getContent();
            BayesVariableState varState =  varStates[var.getId()];
            varState.setDistribution( new double[ varState.getDistribution().length]);
        }
    }

    public void setTargetClass(Class<T> targetClass) {
    }



    public GlobalUpdateListener getGlobalUpdateListener() {
        return globalUpdateListener;
    }

    public void setGlobalUpdateListener(GlobalUpdateListener globalUpdateListener) {
        this.globalUpdateListener = globalUpdateListener;
    }

    public PassMessageListener getPassMessageListener() {
        return passMessageListener;
    }

    public void setPassMessageListener(PassMessageListener passMessageListener) {
        this.passMessageListener = passMessageListener;
    }

    public Map<String, BayesVariable> getVariables() {
        return variables;
    }

    public Map<String, BayesVariable> getFieldNames() {
        return fieldNames;
    }

    public void setDecided(String varName, boolean bool) {

    }

    public void setDecided(BayesVariable var, boolean bool) {
        // note this is reversed, when the bit is on, the var is undecided. Default state is decided
        if ( !bool ) {
            decided = decided.set(var.getId());
        } else {
            decided = decided.reset(var.getId());
        }
    }

    public boolean isDecided() {
        return decided.isEmpty();
    }

    public boolean isDirty() {
        return !dirty.isEmpty();
    }

    public void setLikelyhood(String varName, double[] distribution) {
        BayesVariable var = variables.get( varName );
        if (  var == null ) {
            throw new IllegalArgumentException("Variable name does not exist: " + varName);
        }
        setLikelyhood( var, distribution );
    }

    public void unsetLikelyhood(BayesVariable var) {
        int id = var.getId();
        previousLikelyhoods[id] = this.likelyhoods[id];
        this.likelyhoods[id] = null;
        dirty = dirty.set(id);
    }

    public void unsetLikelyhood(String varName) {
        BayesVariable var = variables.get(varName);
        if (var == null) {
            throw new IllegalArgumentException("Variable name does not exist: " + varName);
        }
        unsetLikelyhood(var);
    }


    public void setLikelyhood(BayesVariable var, double[] distribution) {
        GraphNode node = graph.getNode( var.getId() );
        JunctionTreeClique clique = tree.getJunctionTreeNodes( )[var.getFamily()];

        setLikelyhood( new BayesLikelyhood(graph, clique, node, distribution ) );
    }

    public void setLikelyhood(BayesLikelyhood likelyhood) {
        int id = likelyhood.getVariable().getId();
        BayesLikelyhood old = this.likelyhoods[id];
        if ( old == null || !old.equals( likelyhood ) ) {
            previousLikelyhoods[id] = old;
            this.likelyhoods[likelyhood.getVariable().getId()] = likelyhood;
            dirty = dirty.set(id);
        }
    }

    public void setHardEvidence(String varName, String state) {
        BayesVariable var = variables.get(varName);
        if (var == null) {
            throw new IllegalArgumentException("Variable name does not exist: " + varName);
        }
        Object[] outcomes   = var.getOutcomes();
        int      stateIndex = -1;
        for (int i = 0; i < outcomes.length; i++) {
            if (state.equals(outcomes[i])) {
                stateIndex = i;
                break;
            }
        }
        if (stateIndex == -1) {
            throw new IllegalArgumentException("State '" + state + "' does not exist for variable '" + varName + "'");
        }
        setHardEvidence(var, stateIndex);
    }

    public void setHardEvidence(BayesVariable var, int stateIndex) {
        Object[] outcomes = var.getOutcomes();
        if (stateIndex < 0 || stateIndex >= outcomes.length) {
            throw new IndexOutOfBoundsException("State index " + stateIndex + " out of range for variable '" + var.getName() + "' with " + outcomes.length + " states");
        }
        double[] distribution = new double[outcomes.length];
        distribution[stateIndex] = 1.0;
        setLikelyhood(var, distribution);
    }

    public void batchSetEvidence(Map<String, double[]> evidence) {
        for (int i = 0; i < likelyhoods.length; i++) {
            if (likelyhoods[i] != null) {
                String varName = likelyhoods[i].getVariable().getName();
                if (!evidence.containsKey(varName)) {
                    BayesVariable var = variables.get(varName);
                    unsetLikelyhood(var);
                }
            }
        }
        for (Map.Entry<String, double[]> entry : evidence.entrySet()) {
            setLikelyhood(entry.getKey(), entry.getValue());
        }
    }


    public void globalUpdate() {
        if (!isDecided()) {
            throw new IllegalStateException("Cannot perform global update while one or more variables are undecided");
        }
        if (calibrated && !isDirty()) {                    // Phase 1
            return;
        }

        int dirtyCount = 0;
        for (int i = 0; i < likelyhoods.length; i++) {
            if (dirty.isSet(i)) {dirtyCount++;}
        }
        statDirtyCount = dirtyCount;
        long startNanos = System.nanoTime();

        boolean dirtySubtreesComputed = false;
        if (calibrated && enableFastRetract) {
            computeDirtySubtrees();
            dirtySubtreesComputed = true;
            if (isRetractable()) {                         // Phase 3
                if (globalUpdateListener != null) {
                    globalUpdateListener.beforeGlobalUpdate(cliqueStates[tree.getRoot().getId()]);
                }
                retractionUpdate();
                saveCalibrated();
                dirty      = BitMask.getEmpty(likelyhoods.length);
                calibrated = true;
                statRetractionCount++;
                statLastPropNanos = System.nanoTime() - startNanos;
                if (globalUpdateListener != null) {
                    globalUpdateListener.afterGlobalUpdate(cliqueStates[tree.getRoot().getId()]);
                }
                return;
            }
        }

        if (calibrated) {                                  // Phase 2 setup
            if (!dirtySubtreesComputed) {computeDirtySubtrees();}
        }

        if (isDirty()) {
            reset();
        }
        applyEvidence();

        if (globalUpdateListener != null) {
            globalUpdateListener.beforeGlobalUpdate(cliqueStates[tree.getRoot().getId()]);
        }

        if (calibrated) {
            collectEvidenceIncremental(tree.getRoot());
        } else {
            collectEvidence(tree.getRoot());
        }

        snapshotSeparators();
        distributeEvidence(tree.getRoot());

        if (enableFastRetract) {
            saveCalibrated();
        }

        dirty      = BitMask.getEmpty(likelyhoods.length);
        calibrated = true;
        Arrays.fill(subtreeHasDirty, false);
        statLastPropNanos = System.nanoTime() - startNanos;

        if (globalUpdateListener != null) {
            globalUpdateListener.afterGlobalUpdate(cliqueStates[tree.getRoot().getId()]);
        }
    }

    /**
     * For a clean (non-dirty) subtree: restore the calibrated separator snapshot
     * and absorb it into the target clique. After reset(), separator potentials are
     * all 1.0, so oldSepPots = {1.0,...} and absorb gives:
     *   target[i] = targetInit[i] * snapshot[j] / 1.0
     * which correctly injects the previously calibrated message without recomputing it.
     */
    private void injectSnapshotAndAbsorb(JunctionTreeSeparator sep, JunctionTreeClique targetClique) {
        double[] sepPots = separatorStates[sep.getId()].getPotentials();

        // oldSepPots = current post-reset state = all 1.0
        double[] oldSepPots = Arrays.copyOf(sepPots, sepPots.length);

        // Restore the calibrated snapshot into the separator
        System.arraycopy(sepPotSnapshots[sep.getId()], 0, sepPots, 0, sepPots.length);

        // Absorb snapshot into target (no project — source clique not involved)
        BayesVariable[] sepVars = sep.getValues().toArray(new BayesVariable[sep.getValues().size()]);
        absorb(sepVars, cliqueStates[targetClique.getId()], separatorStates[sep.getId()], oldSepPots);
    }

    private void collectChildEvidenceIncremental(JunctionTreeClique clique, JunctionTreeClique startClique) {
        List<JunctionTreeSeparator> seps = clique.getChildren();
        for (JunctionTreeSeparator sep : seps) {
            collectChildEvidenceIncremental(sep.getChild(), startClique);
        }

        if (clique.getParentSeparator() != null && clique != startClique) {
            JunctionTreeSeparator parentSep = clique.getParentSeparator();
            JunctionTreeClique parent = parentSep.getParent();

            if (subtreeHasDirty[clique.getId()]) {
                passMessage(clique, parentSep, parent); // dirty: recompute
            } else {
                injectSnapshotAndAbsorb(parentSep, parent); // clean: inject snapshot
            }
        }
    }

    private void collectEvidenceIncremental(JunctionTreeClique clique) {
        if (clique.getParentSeparator() != null) {
            // Non-root start: fall back to full path (unreachable via globalUpdate())
            collectParentEvidence(clique.getParentSeparator().getParent(),
                    clique.getParentSeparator(), clique, clique);
        }
        collectChildEvidenceIncremental(clique, clique);
    }

    /**
     * Always performs the full reset + applyEvidence + collect + distribute sequence,
     * bypassing all incremental optimisations. Used by benchmarks as the permanent baseline.
     */
    public void globalUpdateForced() {
        if ( !isDecided() ) {
            throw new IllegalStateException("Cannot perform global update while one or more variables are undecided");
        }
        reset();
        applyEvidence();
        globalUpdate(tree.getRoot());
        snapshotSeparators();
        saveCalibrated();
        dirty = BitMask.getEmpty(likelyhoods.length);
        calibrated = true;
    }

    public void applyEvidence() {
        for ( int i = 0; i < likelyhoods.length; i++ ) {
            BayesLikelyhood l = likelyhoods[i];
            if ( l != null ) {
                int family = likelyhoods[i].getVariable().getFamily();
                JunctionTreeClique node = tree.getJunctionTreeNodes()[family];
                likelyhoods[i].multiplyInto(cliqueStates[family].getPotentials());
                BayesAbsorption.normalize(cliqueStates[family].getPotentials());
            }
        }

    }

    public void globalUpdate(JunctionTreeClique clique) {
        if ( globalUpdateListener != null ) {
            globalUpdateListener.beforeGlobalUpdate(cliqueStates[clique.getId()]);
        }
        collectEvidence( clique );
        distributeEvidence( clique );
        if ( globalUpdateListener != null ) {
            globalUpdateListener.afterGlobalUpdate(cliqueStates[clique.getId()]);
        }
    }

    public void recurseGlobalUpdate(JunctionTreeClique clique) {
        globalUpdate(clique);

        List<JunctionTreeSeparator> seps = clique.getChildren();
        for ( JunctionTreeSeparator sep : seps ) {
            recurseGlobalUpdate(sep.getChild());
        }
    }

    public void collectEvidence(JunctionTreeClique clique) {
        if ( clique.getParentSeparator() != null ) {
            collectParentEvidence(clique.getParentSeparator().getParent(), clique.getParentSeparator(), clique, clique);
        }

        collectChildEvidence(clique, clique);
    }

    public void collectParentEvidence(JunctionTreeClique clique, JunctionTreeSeparator sep, JunctionTreeClique child, JunctionTreeClique startClique) {
        if ( clique.getParentSeparator() != null ) {
            collectParentEvidence(clique.getParentSeparator().getParent(), clique.getParentSeparator(),
                                  clique,
                                  startClique);
        }

        List<JunctionTreeSeparator> seps = clique.getChildren();
        for ( JunctionTreeSeparator childSep : seps ) {
            if ( childSep.getChild() == child )  {
                // ensure that when called from collectParentEvidence it does not re-enter the same node
                continue;
            }
            collectChildEvidence(childSep.getChild(), startClique);
        }

        passMessage(clique, child.getParentSeparator(), child );
    }


    public void collectChildEvidence(JunctionTreeClique clique, JunctionTreeClique startClique) {
        List<JunctionTreeSeparator> seps = clique.getChildren();
        for ( JunctionTreeSeparator sep : seps ) {
            collectChildEvidence(sep.getChild(), startClique);
        }

        if ( clique.getParentSeparator() != null && clique != startClique ) {
            // root has no parent, so we need to check.
            // Do not propogate the start node into another node
            passMessage(clique, clique.getParentSeparator(), clique.getParentSeparator().getParent() );
        }
    }

    public void distributeEvidence(JunctionTreeClique clique) {
        if ( clique.getParentSeparator() != null ) {
            distributeParentEvidence(clique.getParentSeparator().getParent(), clique.getParentSeparator(), clique, clique);
        }

        distributeChildEvidence(clique, clique);
    }

    public void distributeParentEvidence(JunctionTreeClique clique, JunctionTreeSeparator sep, JunctionTreeClique child, JunctionTreeClique startClique) {
        passMessage(child, child.getParentSeparator(), clique);

        if ( clique.getParentSeparator() != null ) {
            distributeParentEvidence(clique.getParentSeparator().getParent(), clique.getParentSeparator(),
                                     clique,
                                     startClique);
        }

        List<JunctionTreeSeparator> seps = clique.getChildren();
        for ( JunctionTreeSeparator childSep : seps ) {
            if ( childSep.getChild() == child )  {
                // ensure that when called from distributeParentEvidence it does not re-enter the same node
                continue;
            }
            distributeChildEvidence(childSep.getChild(), startClique);
        }
    }


    public void distributeChildEvidence(JunctionTreeClique clique, JunctionTreeClique startClique) {
        if ( clique.getParentSeparator() != null && clique != startClique ) {
            // root has no parent, so we need to check.
            // Do not propogate the start node into another node
            passMessage( clique.getParentSeparator().getParent(), clique.getParentSeparator(), clique );
        }

        List<JunctionTreeSeparator> seps = clique.getChildren();
        for ( JunctionTreeSeparator sep : seps ) {
            distributeChildEvidence(sep.getChild(), startClique);
        }
    }


    /**
     * Passes a message from node1 to node2.
     * node1 projects its trgPotentials into the separator.
     * node2 then absorbs those trgPotentials from the separator.
     * @param sourceClique
     * @param sep
     * @param targetClique
     */
    public void passMessage( JunctionTreeClique sourceClique, JunctionTreeSeparator sep, JunctionTreeClique targetClique) {
        double[] sepPots = separatorStates[sep.getId()].getPotentials();
        double[] oldSepPots = Arrays.copyOf(sepPots, sepPots.length);

        BayesVariable[] sepVars = sep.getValues().toArray(new BayesVariable[sep.getValues().size()]);

        if ( passMessageListener != null ) {
            passMessageListener.beforeProjectAndAbsorb(sourceClique, sep, targetClique, oldSepPots);
        }

        project(sepVars, cliqueStates[sourceClique.getId()], separatorStates[sep.getId()]);
        if ( passMessageListener != null ) {
            passMessageListener.afterProject(sourceClique, sep, targetClique, oldSepPots);
        }

        absorb(sepVars, cliqueStates[targetClique.getId()], separatorStates[sep.getId()], oldSepPots);
        LogSpace.normalize(cliqueStates[targetClique.getId()].getPotentials());
        if ( passMessageListener != null ) {
            passMessageListener.afterAbsorb(sourceClique, sep, targetClique, oldSepPots);
        }
    }

    //private static void project(BayesVariable[] sepVars, JunctionTreeNode node, JunctionTreeSeparator sep) {
    private static void project(BayesVariable[] sepVars, CliqueState clique, SeparatorState separator) {
        //JunctionTreeNode node, JunctionTreeSeparator sep
        BayesVariable[] vars = clique.getJunctionTreeClique().getValues().toArray(new BayesVariable[clique.getJunctionTreeClique().getValues().size()]);
        int[] sepVarPos = PotentialMultiplier.createSubsetVarPos(vars, sepVars);

        int sepVarNumberOfStates = PotentialMultiplier.createNumberOfStates(sepVars);
        int[] sepVarMultipliers = PotentialMultiplier.createIndexMultipliers(sepVars, sepVarNumberOfStates);

        BayesProjection p = new BayesProjection(vars, clique.getPotentials(), sepVarPos, sepVarMultipliers, separator.getPotentials());
        p.project();
    }

    //private static void absorb(BayesVariable[] sepVars, JunctionTreeNode node, JunctionTreeSeparator sep, double[] oldSepPots ) {
    private static void absorb(BayesVariable[] sepVars, CliqueState clique, SeparatorState separator, double[] oldSepPots ) {
        //BayesVariable[] vars = node.getValues().toArray( new BayesVariable[node.getValues().size()] );
        BayesVariable[] vars = clique.getJunctionTreeClique().getValues().toArray(new BayesVariable[clique.getJunctionTreeClique().getValues().size()]);

        int[] sepVarPos = PotentialMultiplier.createSubsetVarPos(vars, sepVars);

        int sepVarNumberOfStates = PotentialMultiplier.createNumberOfStates(sepVars);
        int[] sepVarMultipliers = PotentialMultiplier.createIndexMultipliers(sepVars, sepVarNumberOfStates);

        BayesAbsorption p = new BayesAbsorption(sepVarPos, oldSepPots, separator.getPotentials(), sepVarMultipliers, vars, clique.getPotentials());
        p.absorb();
    }

    public BayesVariableState marginalize(String name) {
        BayesVariable var = this.variables.get(name);
        if ( var == null ) {
            throw new IllegalArgumentException("Variable name does not exist '" + name + "'" );
        }
        BayesVariableState varState = varStates[var.getId()];
        marginalize( varState );
        return varState;
    }

    public void marginalize(BayesVariableState varState) {
        CliqueState        cliqueState = cliqueStates[varState.getVariable().getFamily()];
        JunctionTreeClique jtNode      = cliqueState.getJunctionTreeClique();
        new Marginalizer(jtNode.getValues().toArray(new BayesVariable[jtNode.getValues().size()]), cliqueState.getPotentials(), varState.getVariable(), varState.getDistribution());
        LogSpace.toProbSpace(varState.getDistribution());
    }

    public SeparatorState[] getSeparatorStates() {
        return separatorStates;
    }

    public CliqueState[] getCliqueStates() {
        return cliqueStates;
    }

    public BayesVariableState[] getVarStates() {
        return varStates;
    }

    public BayesStats getStats() {
        int evidenceCount = 0;
        for (BayesLikelyhood l : likelyhoods) {
            if (l != null) {evidenceCount++;}
        }
        int maxCliqueSize = 0;
        for (JunctionTreeClique clique : tree.getJunctionTreeNodes()) {
            int size = clique.getValues().size();
            if (size > maxCliqueSize) {maxCliqueSize = size;}
        }
        return new BayesStats(
                statDirtyCount,
                statLastPropNanos,
                statRetractionCount,
                tree.getJunctionTreeNodes().length,
                maxCliqueSize,
                evidenceCount
        );
    }

    public double sensitivity(String evidenceVariable, String targetVariable) {
        BayesVariable evVar = variables.get(evidenceVariable);
        if (evVar == null) {
            throw new IllegalArgumentException("Variable name does not exist: " + evidenceVariable);
        }
        if (likelyhoods[evVar.getId()] == null) {
            return 0.0;
        }
        BayesVariable tgtVar = variables.get(targetVariable);
        if (tgtVar == null) {
            throw new IllegalArgumentException("Variable name does not exist: " + targetVariable);
        }

        double[] currentPosterior = marginalize(targetVariable).getDistribution().clone();

        BayesInstance<?> without = new BayesInstance<>(tree);
        for (int i = 0; i < likelyhoods.length; i++) {
            if (i != evVar.getId() && likelyhoods[i] != null) {
                without.setLikelyhood(likelyhoods[i]);
            }
        }
        without.globalUpdateForced();
        double[] withoutPosterior = without.marginalize(targetVariable).getDistribution();

        return klDivergence(currentPosterior, withoutPosterior);
    }

    static double klDivergence(double[] p, double[] q) {
        double kl = 0.0;
        for (int i = 0; i < p.length; i++) {
            if (p[i] > 0.0 && q[i] > 0.0) {
                kl += p[i] * Math.log(p[i] / q[i]);
            } else if (p[i] > 0.0) {
                return Double.POSITIVE_INFINITY;
            }
        }
        return kl;
    }

    public MapAssignment mapInference() {
        CliqueState[] mapCliques = new CliqueState[cliqueStates.length];
        for (JunctionTreeClique clique : tree.getJunctionTreeNodes()) {
            mapCliques[clique.getId()] = clique.createState();
        }
        SeparatorState[] mapSeps = new SeparatorState[separatorStates.length];
        for (JunctionTreeSeparator sep : tree.getJunctionTreeSeparators()) {
            mapSeps[sep.getId()] = sep.createState();
        }

        for (int i = 0; i < likelyhoods.length; i++) {
            BayesLikelyhood l = likelyhoods[i];
            if (l != null) {
                int family = l.getVariable().getFamily();
                l.multiplyInto(mapCliques[family].getPotentials());
                BayesAbsorption.normalize(mapCliques[family].getPotentials());
            }
        }

        mapCollectChild(tree.getRoot(), tree.getRoot(), mapCliques, mapSeps);
        mapDistributeChild(tree.getRoot(), tree.getRoot(), mapCliques, mapSeps);

        Map<String, Object> assignment = new LinkedHashMap<>();
        for (Map.Entry<String, BayesVariable> entry : variables.entrySet()) {
            BayesVariable var         = entry.getValue();
            double[]      maxMarginal = mapMaxMarginalize(var, mapCliques);
            int           best        = 0;
            for (int i = 1; i < maxMarginal.length; i++) {
                if (maxMarginal[i] > maxMarginal[best]) {best = i;}
            }
            assignment.put(var.getName(), var.getOutcomes()[best]);
        }

        return new MapAssignment(assignment);
    }

    private void mapCollectChild(JunctionTreeClique clique, JunctionTreeClique startClique,
                                 CliqueState[] cliques, SeparatorState[] seps) {
        for (JunctionTreeSeparator sep : clique.getChildren()) {
            mapCollectChild(sep.getChild(), startClique, cliques, seps);
        }
        if (clique.getParentSeparator() != null && clique != startClique) {
            mapPassMessage(clique, clique.getParentSeparator(),
                           clique.getParentSeparator().getParent(), cliques, seps);
        }
    }

    private void mapDistributeChild(JunctionTreeClique clique, JunctionTreeClique startClique,
                                    CliqueState[] cliques, SeparatorState[] seps) {
        if (clique.getParentSeparator() != null && clique != startClique) {
            mapPassMessage(clique.getParentSeparator().getParent(),
                           clique.getParentSeparator(), clique, cliques, seps);
        }
        for (JunctionTreeSeparator sep : clique.getChildren()) {
            mapDistributeChild(sep.getChild(), startClique, cliques, seps);
        }
    }

    private void mapPassMessage(JunctionTreeClique sourceClique, JunctionTreeSeparator sep,
                                JunctionTreeClique targetClique,
                                CliqueState[] cliques, SeparatorState[] seps) {
        double[] sepPots    = seps[sep.getId()].getPotentials();
        double[] oldSepPots = Arrays.copyOf(sepPots, sepPots.length);

        BayesVariable[] sepVars = sep.getValues().toArray(new BayesVariable[sep.getValues().size()]);

        mapProject(sepVars, cliques[sourceClique.getId()], seps[sep.getId()]);
        absorb(sepVars, cliques[targetClique.getId()], seps[sep.getId()], oldSepPots);
        LogSpace.normalize(cliques[targetClique.getId()].getPotentials());
    }

    private static void mapProject(BayesVariable[] sepVars, CliqueState clique, SeparatorState separator) {
        BayesVariable[] vars = clique.getJunctionTreeClique().getValues().toArray(
                new BayesVariable[clique.getJunctionTreeClique().getValues().size()]);
        int[] sepVarPos            = PotentialMultiplier.createSubsetVarPos(vars, sepVars);
        int   sepVarNumberOfStates = PotentialMultiplier.createNumberOfStates(sepVars);
        int[] sepVarMultipliers    = PotentialMultiplier.createIndexMultipliers(sepVars, sepVarNumberOfStates);

        MaxProjection p = new MaxProjection(vars, clique.getPotentials(),
                                            sepVarPos, sepVarMultipliers, separator.getPotentials());
        p.project();
    }

    private double[] mapMaxMarginalize(BayesVariable var, CliqueState[] cliques) {
        CliqueState        cliqueState = cliques[var.getFamily()];
        JunctionTreeClique jtNode      = cliqueState.getJunctionTreeClique();
        BayesVariable[]    srcVars     = jtNode.getValues().toArray(new BayesVariable[jtNode.getValues().size()]);
        BayesVariable[]    trgVars     = new BayesVariable[]{var};

        int[] trgVarPos            = PotentialMultiplier.createSubsetVarPos(srcVars, trgVars);
        int   trgVarNumberOfStates = PotentialMultiplier.createNumberOfStates(trgVars);
        int[] trgVarMultipliers    = PotentialMultiplier.createIndexMultipliers(trgVars, trgVarNumberOfStates);

        double[] result = new double[var.getOutcomes().length];
        new MaxProjection(srcVars, cliqueState.getPotentials(),
                          trgVarPos, trgVarMultipliers, result).project();
        LogSpace.toProbSpace(result);
        return result;
    }


}

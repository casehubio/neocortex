package io.casehub.neocortex.caps.engine;

import io.casehub.neocortex.caps.CapsTopology;
import io.casehub.neocortex.caps.SituationActivation;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class RuleBasedSituationClassifierTest {

    private static RuleBasedSituationClassifier classifier;

    @BeforeAll
    static void loadTopology() {
        CapsTopology topology = new CapsTopologyLoader().loadFromClasspath("caps-topology.yaml");
        classifier = new RuleBasedSituationClassifier(topology);
    }

    @Test
    void emptyDescriptionReturnsEmpty() {
        assertThat(classifier.classify("", Map.of())).isEmpty();
        assertThat(classifier.classify(null, Map.of())).isEmpty();
    }

    @Test
    void rewardKeywordMatchesRewardNode() {
        List<SituationActivation> result = classifier.classify(
            "She was rewarded for her hard work and praised by the teacher",
            Map.of());

        assertThat(result).anySatisfy(a -> {
            assertThat(a.nodeId()).isEqualTo("reward");
            assertThat(a.confidence()).isGreaterThanOrEqualTo(0.3);
        });
    }

    @Test
    void threatKeywordsMatchThreatNodes() {
        List<SituationActivation> result = classifier.classify(
            "He was attacked by a violent stranger who hurt him badly",
            Map.of());

        assertThat(result).anySatisfy(a ->
            assertThat(a.nodeId()).isEqualTo("physical_threat"));
    }

    @Test
    void multiWordKeywordsMatch() {
        List<SituationActivation> result = classifier.classify(
            "She felt left out and ostracized by her peers",
            Map.of());

        assertThat(result).anySatisfy(a ->
            assertThat(a.nodeId()).isEqualTo("exclusion"));
    }

    @Test
    void multipleNodesCanActivate() {
        List<SituationActivation> result = classifier.classify(
            "He was rejected and abandoned by his family, left alone and deserted",
            Map.of());

        List<String> nodeIds = result.stream()
            .map(SituationActivation::nodeId).toList();
        assertThat(nodeIds).contains("rejection", "abandonment");
    }

    @Test
    void lowConfidenceFilteredOut() {
        List<SituationActivation> result = classifier.classify(
            "The weather was nice today and the sun was shining",
            Map.of());

        assertThat(result).isEmpty();
    }

    @Test
    void attachmentKeywordsMatch() {
        List<SituationActivation> result = classifier.classify(
            "A warm, nurturing and caring environment with consistent, reliable support",
            Map.of());

        assertThat(result).anySatisfy(a ->
            assertThat(a.nodeId()).isEqualTo("secure_attachment"));
    }

    @Test
    void autonomyKeywordsMatch() {
        List<SituationActivation> result = classifier.classify(
            "She felt powerless, trapped, and helpless with no control over the situation",
            Map.of());

        assertThat(result).anySatisfy(a ->
            assertThat(a.nodeId()).isEqualTo("powerlessness"));
    }
}

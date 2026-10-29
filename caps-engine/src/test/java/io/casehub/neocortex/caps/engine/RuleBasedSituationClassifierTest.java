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
    void classifyReturnsEmptyWhenKeywordMatchingDisabled() {
        assertThat(classifier.classify("test", Map.of())).isEmpty();
    }

    @Test
    void emptyDescriptionReturnsEmpty() {
        assertThat(classifier.classifyByKeyword("", Map.of(), null)).isEmpty();
        assertThat(classifier.classifyByKeyword(null, Map.of(), null)).isEmpty();
    }

    @Test
    void rewardKeywordMatchesRewardNode() {
        List<SituationActivation> result = classifier.classifyByKeyword(
            "She was rewarded for her hard work and praised by the teacher",
            Map.of(), null);

        assertThat(result).anySatisfy(a -> {
            assertThat(a.nodeId()).isEqualTo("reward");
            assertThat(a.confidence()).isGreaterThanOrEqualTo(0.3);
        });
    }

    @Test
    void threatKeywordsMatchThreatNodes() {
        List<SituationActivation> result = classifier.classifyByKeyword(
            "He was attacked by a violent stranger who hurt him badly",
            Map.of(), null);

        assertThat(result).anySatisfy(a ->
            assertThat(a.nodeId()).isEqualTo("physical_threat"));
    }

    @Test
    void multiWordKeywordsMatch() {
        List<SituationActivation> result = classifier.classifyByKeyword(
            "She felt left out and ostracized by her peers",
            Map.of(), null);

        assertThat(result).anySatisfy(a ->
            assertThat(a.nodeId()).isEqualTo("exclusion"));
    }

    @Test
    void multipleNodesCanActivate() {
        List<SituationActivation> result = classifier.classifyByKeyword(
            "He was rejected and abandoned by his family, left alone and deserted",
            Map.of(), null);

        List<String> nodeIds = result.stream()
            .map(SituationActivation::nodeId).toList();
        assertThat(nodeIds).contains("rejection", "abandonment");
    }

    @Test
    void lowConfidenceFilteredOut() {
        List<SituationActivation> result = classifier.classifyByKeyword(
            "The weather was nice today and the sun was shining",
            Map.of(), null);

        assertThat(result).isEmpty();
    }

    @Test
    void attachmentKeywordsMatch() {
        List<SituationActivation> result = classifier.classifyByKeyword(
            "A warm, nurturing and caring environment with consistent, reliable support",
            Map.of(), null);

        assertThat(result).anySatisfy(a ->
            assertThat(a.nodeId()).isEqualTo("secure_attachment"));
    }

    @Test
    void autonomyKeywordsMatch() {
        List<SituationActivation> result = classifier.classifyByKeyword(
            "She felt powerless, trapped, and helpless with no control over the situation",
            Map.of(), null);

        assertThat(result).anySatisfy(a ->
            assertThat(a.nodeId()).isEqualTo("powerlessness"));
    }

    @Test
    void strongVocabularyTermProducesHighConfidence() {
        List<SituationActivation> result = classifier.classifyByKeyword(
                "He was attacked violently and felt threatened",
                Map.of(), null);

        var threat = result.stream()
                           .filter(a -> a.nodeId().equals("physical_threat"))
                           .findFirst().orElseThrow();

        assertThat(threat.confidence()).isGreaterThanOrEqualTo(0.5);
    }

    @Test
    void multipleMatchesBoostConfidence() {
        List<SituationActivation> singleMatch = classifier.classifyByKeyword(
                "She was rewarded", Map.of(), null);
        List<SituationActivation> multiMatch = classifier.classifyByKeyword(
                "She was rewarded and praised, and earned a bonus", Map.of(), null);

        double singleConf = singleMatch.stream()
                                       .filter(a -> a.nodeId().equals("reward"))
                                       .mapToDouble(SituationActivation::confidence).findFirst().orElse(0);
        double multiConf = multiMatch.stream()
                                     .filter(a -> a.nodeId().equals("reward"))
                                     .mapToDouble(SituationActivation::confidence).findFirst().orElse(0);

        assertThat(multiConf).isGreaterThan(singleConf);
    }

    @Test
    void scenarioEvent_rivalFailure_activatesDominanceAndFailure() {
        List<SituationActivation> result = classifier.classifyByKeyword(
                "The rival's scheme collapsed spectacularly — they stumbled and failed in front of everyone, humiliated and defeated",
                Map.of(), null);

        List<String> nodeIds = result.stream().map(SituationActivation::nodeId).toList();
        assertThat(nodeIds).contains("failure", "dominance");
    }

    @Test
    void scenarioEvent_helpingOthers_activatesAcceptanceAndSharing() {
        List<SituationActivation> result = classifier.classifyByKeyword(
                "She helped the newcomer feel welcomed and included, sharing what she had",
                Map.of(), null);

        List<String> nodeIds = result.stream().map(SituationActivation::nodeId).toList();
        assertThat(nodeIds).contains("acceptance", "sharing");
    }

    @Test
    void scenarioEvent_competitionAndRecognition_activatesMultipleNodes() {
        List<SituationActivation> result = classifier.classifyByKeyword(
                "His clever scheme outwitted the rival and everyone praised his brilliant plan",
                Map.of(), null);

        List<String> nodeIds = result.stream().map(SituationActivation::nodeId).toList();
        assertThat(nodeIds).contains("competence_recognition", "competition");
    }


}

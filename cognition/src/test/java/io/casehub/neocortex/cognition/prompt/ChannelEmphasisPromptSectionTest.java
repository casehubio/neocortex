package io.casehub.neocortex.cognition.prompt;

import io.casehub.neocortex.cognition.appraisal.*;
import io.casehub.neocortex.cognition.core.CognitionConfig;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class ChannelEmphasisPromptSectionTest {

    @Test
    void rendersNullWhenNoWeights() {
        var participant = participantReturning(Optional.empty());
        var section = new ChannelEmphasisPromptSection(participant);
        var context = new CognitionRenderContext("a1", "t1", null);

        assertThat(section.render(context)).isNull();
    }

    @Test
    void rendersNullForDefaultWeights() {
        var participant = participantReturning(Optional.of(ChannelWeightProfile.DEFAULT));
        var section = new ChannelEmphasisPromptSection(participant);
        var context = new CognitionRenderContext("a1", "t1", null);

        assertThat(section.render(context)).isNull();
    }

    @Test
    void foregroundsSomaticUnderThreat() {
        var threatWeights = new ChannelWeightProfile(0.35, 0.10, 0.15, 0.40);
        var participant = participantReturning(Optional.of(threatWeights));
        var section = new ChannelEmphasisPromptSection(participant);
        var context = new CognitionRenderContext("a1", "t1", null);

        var rendered = section.render(context);
        assertThat(rendered).isNotNull();
        assertThat(rendered).contains("Foreground your somatic");
        assertThat(rendered).contains("Foreground your action tendency");
        assertThat(rendered).contains("Background your attentional");
    }

    @Test
    void foregroundsAttentionalInSocialSituation() {
        var socialWeights = new ChannelWeightProfile(0.20, 0.35, 0.15, 0.30);
        var participant = participantReturning(Optional.of(socialWeights));
        var section = new ChannelEmphasisPromptSection(participant);
        var context = new CognitionRenderContext("a1", "t1", null);

        var rendered = section.render(context);
        assertThat(rendered).isNotNull();
        assertThat(rendered).contains("Foreground your attentional");
        assertThat(rendered).contains("Foreground your action tendency");
        assertThat(rendered).contains("Background your temporal");
    }

    @Test
    void foregroundsTemporalInResourceSituation() {
        var resourceWeights = new ChannelWeightProfile(0.15, 0.25, 0.30, 0.30);
        var participant = participantReturning(Optional.of(resourceWeights));
        var section = new ChannelEmphasisPromptSection(participant);
        var context = new CognitionRenderContext("a1", "t1", null);

        var rendered = section.render(context);
        assertThat(rendered).isNotNull();
        assertThat(rendered).contains("Foreground your temporal");
        assertThat(rendered).contains("Foreground your action tendency");
        assertThat(rendered).contains("Background your somatic");
    }

    @Test
    void omitsNeutralChannels() {
        // Channels between suppress and emphasis thresholds should not appear
        var weights = new ChannelWeightProfile(0.35, 0.25, 0.25, 0.15);
        var participant = participantReturning(Optional.of(weights));
        var section = new ChannelEmphasisPromptSection(participant);
        var context = new CognitionRenderContext("a1", "t1", null);

        var rendered = section.render(context);
        assertThat(rendered).contains("Foreground your somatic");
        assertThat(rendered).contains("Background your action tendency");
        // Attentional (0.25) and temporal (0.25) should not appear
        assertThat(rendered).doesNotContain("attentional");
        assertThat(rendered).doesNotContain("temporal");
    }

    private AppraisalTickParticipant participantReturning(
            Optional<ChannelWeightProfile> weights) {
        return new AppraisalTickParticipant(
                new NoOpSalienceStrategy(), new NoOpAppraisalStrategy(),
                null, null, null, CognitionConfig.none()) {
            @Override
            public Optional<ChannelWeightProfile> currentChannelWeights(
                    String agentId, String tenantId) {
                return weights;
            }
        };
    }
}

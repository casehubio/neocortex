package io.casehub.neocortex.cognition.subthought;

import io.casehub.neocortex.cognition.core.CognitionTickContext;
import io.casehub.neocortex.cognition.core.SubjectResolver;
import io.casehub.neocortex.cognition.prompt.CognitionRenderContext;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class SubThoughtPromptSectionTest {

    @Test
    void rendersGroupedByEntity() {
        var extractor = new RuleBasedSubThoughtExtractor();
        SubjectResolver resolver = (a, t) -> Set.of();
        var participant = new SubThoughtTickParticipant(extractor, null, resolver);

        var context = new CognitionTickContext("a1", "t1", null, resolver,
                "Sarah seemed distracted. The pasta was excellent. I should bring David next time.");
        extractor.refreshEntityCache("t1", Set.of("Sarah", "David"));
        participant.tick(context);

        var section = new SubThoughtPromptSection(participant);
        var rendered = section.render(new CognitionRenderContext("a1", "t1", null));

        assertNotNull(rendered);
        assertTrue(rendered.contains("About Sarah:"));
        assertTrue(rendered.contains("seemed distracted"));
        assertTrue(rendered.contains("excellent"));
    }

    @Test
    void rendersNullWhenNoSubThoughts() {
        var extractor = new RuleBasedSubThoughtExtractor();
        SubjectResolver resolver = (a, t) -> Set.of();
        var participant = new SubThoughtTickParticipant(extractor, null, resolver);

        var section = new SubThoughtPromptSection(participant);
        var rendered = section.render(new CognitionRenderContext("a1", "t1", null));

        assertNull(rendered);
    }

    @Test
    void rendersNullWhenNoMatches() {
        var extractor = new RuleBasedSubThoughtExtractor();
        SubjectResolver resolver = (a, t) -> Set.of();
        var participant = new SubThoughtTickParticipant(extractor, null, resolver);

        var context = new CognitionTickContext("a1", "t1", null, resolver, "The weather was nice.");
        participant.tick(context);

        var section = new SubThoughtPromptSection(participant);
        var rendered = section.render(new CognitionRenderContext("a1", "t1", null));

        assertNull(rendered);
    }
}

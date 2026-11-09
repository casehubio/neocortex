package io.casehub.neocortex.cognition.prompt;

import io.casehub.neocortex.cognition.drive.CuriosityChannel;
import io.casehub.neocortex.cognition.drive.CuriosityDrive;
import io.casehub.neocortex.cognition.drive.DriveAxis;
import io.casehub.neocortex.cognition.drive.DriveIntensity;
import io.casehub.neocortex.cognition.drive.DriveOrchestrator;
import io.casehub.neocortex.cognition.drive.DriveProfile;
import io.casehub.neocortex.cognition.search.SearchBudget;
import io.casehub.neocortex.cognition.search.SearchBudgetPromptSection;
import io.casehub.neocortex.cognition.search.SearchBudgetTickParticipant;
import io.casehub.neocortex.cognition.search.SearchMotivation;
import io.casehub.neocortex.mindmap.ContinuumPosition;
import io.casehub.neocortex.mindmap.intelligence.CuriosityChannelComputer;
import io.casehub.neocortex.mindmap.intelligence.InformationGapScore;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ContinuumRenderingTest {

    private static final String AGENT = "agent1";
    private static final String TENANT = "tenant1";

    @Test
    void curiositySectionHealthyRendersNormally() {
        var section = createCuriositySection(Map.of());
        var output = section.render(testContext());
        assertNotNull(output);
        assertFalse(output.contains("unbidden"));
        assertFalse(output.contains("intrusive"));
    }

    @Test
    void curiositySectionFixationNarrowsToDominantChannel() {
        var section = createCuriositySection(Map.of("topic-1", ContinuumPosition.FIXATION));
        var output = section.render(testContext());
        assertNotNull(output);
    }

    @Test
    void curiositySectionObsessionRendersIntrusiveQuality() {
        var section = createCuriositySection(Map.of("topic-1", ContinuumPosition.OBSESSION));
        var output = section.render(testContext());
        assertNotNull(output);
        assertTrue(output.contains("unbidden") || output.contains("intrusive"),
                "Obsession should render intrusive quality, got: " + output);
    }

    @Test
    void curiositySectionRuminationRendersWithdrawal() {
        var section = createCuriositySection(Map.of("topic-1", ContinuumPosition.RUMINATION));
        var output = section.render(testContext());
        assertNotNull(output);
    }

    @Test
    void searchBudgetFixationShiftsToPressured() {
        var section = createSearchBudgetSection(Map.of("topic-1", ContinuumPosition.FIXATION));
        var output = section.render(testContext());
        assertNotNull(output);
        assertTrue(output.contains("circling") || output.contains("back"),
                "Fixation should add circling-back framing, got: " + output);
    }

    @Test
    void nullContinuumMapRendersNormally() {
        var section = createCuriositySection(null);
        var output = section.render(testContext());
        assertNotNull(output, "Null continuum map should render without error");
    }

    @Test
    void emptyContinuumMapRendersNormally() {
        var sectionEmpty = createCuriositySection(Map.of());
        var sectionNull = createCuriositySection(null);
        var outputEmpty = sectionEmpty.render(testContext());
        var outputNull = sectionNull.render(testContext());
        assertEquals(outputEmpty, outputNull, "Empty and null maps should produce same output");
    }

    // --- helpers ---

    private CuriosityPromptSection createCuriositySection(
            Map<String, ContinuumPosition> continuum) {
        var curiosityDrive = mock(CuriosityDrive.class);
        var gap = new InformationGapScore("node-1", "test-topic", "CONCEPT",
                0.6, 0.7, 3, 0.5,
                InformationGapScore.GapType.PARTIAL_KNOWLEDGE, 0.0);
        when(curiosityDrive.lastGaps()).thenReturn(List.of(gap));

        var channelComputer = new CuriosityChannelComputer();

        return new CuriosityPromptSection(curiosityDrive, channelComputer,
                null, null, 0.5, 0.5, continuum);
    }

    private SearchBudgetPromptSection createSearchBudgetSection(
            Map<String, ContinuumPosition> continuum) {
        var participant = mock(SearchBudgetTickParticipant.class);
        when(participant.currentBudget(AGENT, TENANT)).thenReturn(
                Optional.of(new SearchBudget(0.5, 0.3, 0.6, 0.4,
                        SearchMotivation.CURIOSITY, false, false, 0.0, 0.0, 0.0, Instant.now())));
        return new SearchBudgetPromptSection(participant, null, continuum);
    }

    private CognitionRenderContext testContext() {
        return new CognitionRenderContext(AGENT, TENANT, null, List.of());
    }
}

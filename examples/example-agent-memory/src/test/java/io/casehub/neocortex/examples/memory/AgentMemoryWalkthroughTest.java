package io.casehub.neocortex.examples.memory;

import io.casehub.neocortex.cognitive.Confidence;
import io.casehub.neocortex.cognitive.ConfidenceOrigin;
import io.casehub.neocortex.cognitive.ModulationFactor;
import io.casehub.neocortex.cognitive.RetrievalModulator;
import io.casehub.neocortex.cognitive.index.ModulationFactors;
import io.casehub.neocortex.cognitive.index.ModulationProfiles;
import io.casehub.neocortex.memory.CaseMemoryStore;
import io.casehub.neocortex.memory.Memory;
import io.casehub.neocortex.memory.MemoryDomain;
import io.casehub.neocortex.memory.MemoryInput;
import io.casehub.neocortex.memory.MemoryOrder;
import io.casehub.neocortex.memory.MemoryQuery;
import io.casehub.neocortex.memory.Subject;
import io.casehub.neocortex.memory.engagement.EngagementEvent;
import io.casehub.neocortex.memory.engagement.EngagementEvents;
import io.casehub.neocortex.memory.experience.Action;
import io.casehub.neocortex.memory.experience.ExperienceAttributeKeys;
import io.casehub.neocortex.memory.experience.ExperienceEvents;
import io.casehub.neocortex.memory.experience.Observation;
import io.casehub.neocortex.memory.experience.Outcome;
import io.casehub.neocortex.memory.inmem.InMemoryMemoryStore;
import io.casehub.neocortex.memory.mood.MoodBaseline;
import io.casehub.platform.api.identity.CurrentPrincipal;
import io.casehub.neocortex.memory.mood.MoodDecay;
import io.casehub.neocortex.memory.mood.MoodEvents;
import io.casehub.neocortex.memory.mood.MoodState;
import io.casehub.neocortex.memory.personality.PersonalityWeights;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestMethodOrder;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Walkthrough: typed agent memory lifecycle.
 *
 * Scenario — an AI teaching assistant tutors two students through a
 * challenging session. The agent records experiences, tracks its own
 * mood, measures engagement with each student, and uses personality-
 * weighted modulation to decide which memories deserve attention.
 *
 * Each test method is a phase:
 *   1. Record experiences — typed events become domain-tagged memories
 *   2. Mood tracking — PAD snapshots with exponential decay toward baseline
 *   3. Engagement measurement — per-student interaction quality signals
 *   4. Memory query patterns — domain, entity, ordering, principal scoping
 *   5. Personality-weighted modulation — disposition shapes retrieval priority
 *   6. Mood congruence — current emotional state biases what surfaces
 */
@Tag("smoke")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class AgentMemoryWalkthroughTest {

    private CaseMemoryStore store;
    private static final String TENANT = "tutoring-session";
    private static final String AGENT = "tutor-agent";
    private static final String STUDENT_A = "student-alice";
    private static final String STUDENT_B = "student-bob";
    private static final Instant SESSION_START = Instant.parse("2026-03-15T09:00:00Z");

    @BeforeAll
    void setUp() {
        CurrentPrincipal principal = new CurrentPrincipal() {
            @Override public String actorId() { return AGENT; }
            @Override public Set<String> groups() { return Set.of(); }
            @Override public String tenancyId() { return TENANT; }
            @Override public boolean isCrossTenantAdmin() { return false; }
        };
        store = new InMemoryMemoryStore(principal);
    }

    // ── Phase 1: Record Experiences ────────────────────────────────

    @Test
    @Order(1)
    void phase1_recordExperiences() {
        // The tutor agent observes a student asking a question.
        // ExperienceEvents.toMemoryInput() converts the typed event into
        // a MemoryInput with domain="experience" and structured attributes.
        var observation = new Observation(
            AGENT, TENANT, "case-101", "turn-1", SESSION_START,
            "Alice asks about recursion in tree traversal",
            0.9, Map.of(), STUDENT_A);

        MemoryInput obsInput = ExperienceEvents.toMemoryInput(observation);
        String obsId = store.store(obsInput);

        // The agent takes an action — explaining the concept
        var action = new Action(
            AGENT, TENANT, "case-101", "turn-2",
            SESSION_START.plusSeconds(120),
            "Explained recursive depth-first traversal with binary tree example",
            0.85, Map.of(), "explanation");

        store.store(ExperienceEvents.toMemoryInput(action));

        // The outcome — student demonstrates understanding
        var outcome = new Outcome(
            AGENT, TENANT, "case-101", "turn-3",
            SESSION_START.plusSeconds(300),
            "Alice successfully implements pre-order traversal",
            0.95, Map.of(), "correct-implementation", "explanation");

        store.store(ExperienceEvents.toMemoryInput(outcome));

        // Query all experience memories for this agent
        List<Memory> experiences = store.query(
            MemoryQuery.forSubject(
                Subject.of("agent", AGENT),
                ExperienceEvents.DOMAIN, TENANT));

        assertThat(experiences).hasSize(3);

        // The typed attributes survived the conversion — find the observation
        Memory observation1 = experiences.stream()
            .filter(m -> "observation".equals(m.attributes().get(ExperienceAttributeKeys.EVENT_TYPE)))
            .findFirst().orElseThrow();
        assertThat(observation1.attributes().get(ExperienceAttributeKeys.SUBJECT))
            .isEqualTo(STUDENT_A);

        System.out.println("Phase 1 — Recorded 3 experiences:");
        for (Memory m : experiences) {
            System.out.printf("  [%s] %s%n",
                m.attributes().get(ExperienceAttributeKeys.EVENT_TYPE),
                m.text());
        }
    }

    // ── Phase 2: Mood Tracking ────────────────────────────────────

    @Test
    @Order(2)
    void phase2_moodTracking() {
        // The agent starts in a neutral mood
        var sessionStart = new MoodState(
            AGENT, TENANT, SESSION_START,
            0.2, 0.1, 0.3, "session-start", "turn-0", Map.of());

        store.store(MoodEvents.toMemoryInput(sessionStart));

        // After a student asks a very difficult question — arousal spikes
        var underPressure = new MoodState(
            AGENT, TENANT, SESSION_START.plusSeconds(600),
            0.1, 0.7, 0.2, "complex-question-from-bob", "turn-5", Map.of());

        store.store(MoodEvents.toMemoryInput(underPressure));

        // After successfully explaining — pleasure increases
        var afterSuccess = new MoodState(
            AGENT, TENANT, SESSION_START.plusSeconds(900),
            0.7, 0.3, 0.5, "successful-explanation", "turn-8", Map.of());

        store.store(MoodEvents.toMemoryInput(afterSuccess));

        // MoodDecay shows how emotions return toward a baseline over time.
        // The agent's baseline is slightly positive — a naturally encouraging tutor.
        var baseline = new MoodBaseline(0.3, 0.2, 0.4);

        // After 30 minutes with a 20-minute time constant
        MoodState decayed = MoodDecay.decay(
            afterSuccess, baseline,
            Duration.ofMinutes(30), Duration.ofMinutes(20));

        // The high-pleasure state (0.7) has decayed toward baseline (0.3)
        assertThat(decayed.pleasure()).isLessThan(afterSuccess.pleasure());
        assertThat(decayed.pleasure()).isGreaterThan(baseline.pleasure());

        // Arousal also decays toward its resting point
        assertThat(decayed.arousal()).isCloseTo(baseline.arousal(), org.assertj.core.data.Offset.offset(0.05));

        System.out.println("Phase 2 — Mood trajectory:");
        System.out.printf("  Start:     P=%.1f A=%.1f D=%.1f%n",
            sessionStart.pleasure(), sessionStart.arousal(), sessionStart.dominance());
        System.out.printf("  Pressure:  P=%.1f A=%.1f D=%.1f%n",
            underPressure.pleasure(), underPressure.arousal(), underPressure.dominance());
        System.out.printf("  Success:   P=%.1f A=%.1f D=%.1f%n",
            afterSuccess.pleasure(), afterSuccess.arousal(), afterSuccess.dominance());
        System.out.printf("  Decayed:   P=%.2f A=%.2f D=%.2f (30min later)%n",
            decayed.pleasure(), decayed.arousal(), decayed.dominance());
    }

    // ── Phase 3: Engagement Measurement ───────────────────────────

    @Test
    @Order(3)
    void phase3_engagementMeasurement() {
        // Alice is engaged — responds quickly, high affect shift
        var aliceEngagement = new EngagementEvent(
            AGENT, STUDENT_A, TENANT, "case-101", "turn-3",
            SESSION_START.plusSeconds(300),
            "Alice responds to recursion explanation",
            0.9, Map.of(),
            true,       // responded
            1500L,      // responseTimeMs — quick (1.5s)
            250,        // responseLength — detailed response
            0.6,        // affectShift — positive reaction
            3,          // reactionCount — multiple follow-ups
            true);      // continued the conversation

        store.store(EngagementEvents.toMemoryInput(aliceEngagement));

        // Bob is disengaged — slow response, minimal interaction
        var bobEngagement = new EngagementEvent(
            AGENT, STUDENT_B, TENANT, "case-101", "turn-6",
            SESSION_START.plusSeconds(600),
            "Bob responds to sorting algorithm question",
            0.5, Map.of(),
            true,       // responded
            12000L,     // responseTimeMs — very slow (12s)
            30,         // responseLength — terse
            0.1,        // affectShift — minimal reaction
            0,          // reactionCount — no follow-up
            false);     // did not continue

        store.store(EngagementEvents.toMemoryInput(bobEngagement));

        // Query engagement memories
        List<Memory> engagements = store.query(
            MemoryQuery.forSubject(
                Subject.of("agent", AGENT),
                EngagementEvents.DOMAIN, TENANT));

        assertThat(engagements).hasSize(2);

        System.out.println("Phase 3 — Engagement signals:");
        for (Memory m : engagements) {
            String other = m.attributes().get("other-agent");
            String responseTime = m.attributes().get("response-time-ms");
            String affectShift = m.attributes().get("affect-shift");
            System.out.printf("  %s: responseTime=%sms, affectShift=%s — %s%n",
                other, responseTime, affectShift, m.text());
        }
    }

    // ── Phase 4: Memory Query Patterns ────────────────────────────

    @Test
    @Order(4)
    void phase4_memoryQueryPatterns() {
        // Query by domain — all experience memories
        List<Memory> experiences = store.query(
            MemoryQuery.forSubject(
                Subject.of("agent", AGENT),
                ExperienceEvents.DOMAIN, TENANT)
                .withOrder(MemoryOrder.CHRONOLOGICAL));

        assertThat(experiences).isNotEmpty();
        System.out.println("Phase 4 — Query patterns:");
        System.out.printf("  Experience memories (CHRONOLOGICAL): %d%n", experiences.size());

        // Query mood memories
        List<Memory> moods = store.query(
            MemoryQuery.forSubject(
                Subject.of("agent", AGENT),
                MoodEvents.DOMAIN, TENANT)
                .withOrder(MemoryOrder.CHRONOLOGICAL));

        assertThat(moods).hasSize(3);
        System.out.printf("  Mood snapshots: %d%n", moods.size());

        // SALIENCE ordering ranks by recency × confidence.
        // More recent memories with higher confidence surface first.
        List<Memory> salient = store.query(
            MemoryQuery.forSubject(
                Subject.of("agent", AGENT),
                ExperienceEvents.DOMAIN, TENANT)
                .withOrder(MemoryOrder.SALIENCE)
                .withLimit(2));

        assertThat(salient).hasSizeLessThanOrEqualTo(2);
        System.out.printf("  Top-2 salient experiences: %d%n", salient.size());
        for (Memory m : salient) {
            System.out.printf("    → %s%n", m.text());
        }
    }

    // ── Phase 5: Personality-Weighted Modulation ──────────────────

    @Test
    @Order(5)
    void phase5_personalityWeightedModulation() {
        // A personality profile defines how much weight each memory domain carries.
        // This agent values experience memories highly — it learns primarily by doing.
        var personality = new PersonalityWeights(Map.of(
            ExperienceEvents.DOMAIN, 3.0,    // experiences matter most
            MoodEvents.DOMAIN, 0.5,          // mood is secondary
            EngagementEvents.DOMAIN, 1.5     // engagement is moderately important
        ));

        // Collect all memories across domains
        List<Memory> allMemories = new java.util.ArrayList<>();
        for (MemoryDomain domain : List.of(
                ExperienceEvents.DOMAIN, MoodEvents.DOMAIN, EngagementEvents.DOMAIN)) {
            allMemories.addAll(store.query(
                MemoryQuery.forSubject(
                    Subject.of("agent", AGENT), domain, TENANT)));
        }

        assertThat(allMemories).hasSizeGreaterThan(3);

        // Apply modulation: domain weight × recency decay × confidence
        Instant now = SESSION_START.plusSeconds(1800);
        List<ModulationFactor<Memory>> factors = List.of(
            ModulationFactors.domainWeight(personality),
            ModulationFactors.recencyDecay(Duration.ofHours(2), now),
            ModulationFactors.confidenceWeight());

        List<Memory> ranked = RetrievalModulator.modulate(
            allMemories, ModulationProfiles.MEMORY, factors);

        // Experience memories should dominate the top of the list
        // because of the 3.0 weight
        Memory topMemory = ranked.getFirst();
        assertThat(topMemory.domain()).isEqualTo(ExperienceEvents.DOMAIN);

        System.out.println("Phase 5 — Personality-weighted ranking (experience=3.0, mood=0.5, engagement=1.5):");
        for (int i = 0; i < Math.min(5, ranked.size()); i++) {
            Memory m = ranked.get(i);
            System.out.printf("  %d. [%s] %s%n", i + 1, m.domain().name(), m.text());
        }
    }

    // ── Phase 6: Mood Congruence ──────────────────────────────────

    @Test
    @Order(6)
    void phase6_moodCongruence() {
        // Current mood: the tutor is feeling positive after a successful lesson
        var currentMood = new MoodState(
            AGENT, TENANT, SESSION_START.plusSeconds(1800),
            0.8, 0.3, 0.6, "lesson-going-well", "turn-12", Map.of());

        // Store some memories with different emotional signatures
        // — a positive memory (high pleasure)
        store.store(MemoryInput.of(Subject.of("agent", AGENT),
                ExperienceEvents.DOMAIN, TENANT, "Alice solved the problem elegantly")
            .withPad(0.9, 0.2, 0.5)
            .withConfidence(Confidence.stated(0.9, SESSION_START.plusSeconds(1000))));

        // — a neutral memory
        store.store(MemoryInput.of(Subject.of("agent", AGENT),
                ExperienceEvents.DOMAIN, TENANT, "Reviewed syllabus for next week")
            .withPad(0.0, 0.1, 0.3)
            .withConfidence(Confidence.stated(0.9, SESSION_START.plusSeconds(1100))));

        // — a negative memory (low pleasure)
        store.store(MemoryInput.of(Subject.of("agent", AGENT),
                ExperienceEvents.DOMAIN, TENANT, "Bob struggled with basic concepts")
            .withPad(-0.4, 0.5, 0.1)
            .withConfidence(Confidence.stated(0.9, SESSION_START.plusSeconds(1200))));

        // Retrieve all experience memories
        List<Memory> memories = store.query(
            MemoryQuery.forSubject(
                Subject.of("agent", AGENT),
                ExperienceEvents.DOMAIN, TENANT));

        // Mood-congruent modulation: memories with PAD values similar
        // to the current mood rank higher. The tutor is feeling positive (P=0.8),
        // so positive memories should surface.
        Instant now = SESSION_START.plusSeconds(1800);
        List<ModulationFactor<Memory>> factors = List.of(
            ModulationFactors.moodCongruence(currentMood, 0.8),
            ModulationFactors.recencyDecay(Duration.ofHours(4), now));

        List<Memory> congruent = RetrievalModulator.modulate(
            memories, ModulationProfiles.MEMORY, factors);

        System.out.println("Phase 6 — Mood-congruent retrieval (current mood: P=0.8 A=0.3 D=0.6):");
        for (int i = 0; i < Math.min(5, congruent.size()); i++) {
            Memory m = congruent.get(i);
            String pad = String.format("P=%.1f A=%.1f D=%.1f",
                m.pleasure() != null ? m.pleasure() : 0.0,
                m.arousal() != null ? m.arousal() : 0.0,
                m.dominance() != null ? m.dominance() : 0.0);
            System.out.printf("  %d. [%s] %s — %s%n", i + 1, pad, m.domain().name(), m.text());
        }

        // The positive memory should rank higher than the negative one
        // due to mood congruence with the current positive state
        Memory topCongruent = congruent.stream()
            .filter(m -> m.pleasure() != null)
            .findFirst()
            .orElseThrow();
        assertThat(topCongruent.pleasure()).isGreaterThan(0.0);
    }
}

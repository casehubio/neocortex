package io.casehub.neocortex.memory.seeding.biography;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class PersonalityProfileEntryTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Test
    void deserializesFrameworks() throws Exception {
        String json = """
            {
                "id": "pp-1",
                "entity_ref": "Frida Kahlo",
                "frameworks": {
                    "mbti": {"type": "infp", "evidence": "Fi dominant", "confidence": 0.8, "source": "extracted"},
                    "ocean": {"openness": 0.85, "conscientiousness": 0.35, "extraversion": 0.55,
                              "agreeableness": 0.40, "neuroticism": 0.75,
                              "evidence": "High openness", "confidence": 0.8, "source": "extracted"},
                    "jungian_stack": [
                        {"function": "fi", "weight": 0.35, "evidence": "values-driven"},
                        {"function": "ne", "weight": 0.22, "evidence": "possibility exploration"}
                    ],
                    "enneagram": {"type": "type-4", "wing": "5", "evidence": "identity", "confidence": 0.7, "source": "extracted"},
                    "disc": {"primary": "influence", "secondary": "dominance", "evidence": "charismatic", "confidence": 0.7, "source": "extracted"},
                    "belbin": {"primary": "plant", "evidence": "creative", "confidence": 0.65, "source": "extracted"},
                    "thomas_kilmann": {"primary": "competing", "evidence": "confrontational", "confidence": 0.8, "source": "extracted"},
                    "sdi": {"primary": "red", "evidence": "action-oriented", "confidence": 0.7, "source": "extracted"},
                    "ampd": {"callousness": 20, "emotional-lability": 80, "anxiousness": 70,
                             "evidence": "volatile", "confidence": 0.75, "source": "extracted"},
                    "attachment": {"style": "anxious-preoccupied", "anxiety": 0.75, "avoidance": 0.30,
                                   "evidence": "validation-seeking", "confidence": 0.75, "source": "extracted"},
                    "locus_of_control": {"general": "internal", "domains": {"art": "internal", "health": "external"},
                                          "evidence": "strong agency in art", "confidence": 0.7, "source": "extracted"}
                }
            }
            """;
        var entry = MAPPER.readValue(json, PersonalityProfileEntry.class);
        assertThat(entry.entityRef()).isEqualTo("Frida Kahlo");
        assertThat(entry.frameworks().mbti().type()).isEqualTo("infp");
        assertThat(entry.frameworks().ocean().openness()).isEqualTo(0.85);
        assertThat(entry.frameworks().jungianStack()).hasSize(2);
        assertThat(entry.frameworks().jungianStack().getFirst().functionName()).isEqualTo("fi");
        assertThat(entry.frameworks().enneagram().type()).isEqualTo("type-4");
        assertThat(entry.frameworks().enneagram().wing()).isEqualTo("5");
        assertThat(entry.frameworks().disc().primary()).isEqualTo("influence");
        assertThat(entry.frameworks().belbin().primary()).isEqualTo("plant");
        assertThat(entry.frameworks().thomasKilmann().primary()).isEqualTo("competing");
        assertThat(entry.frameworks().sdi().primary()).isEqualTo("red");
        assertThat(entry.frameworks().ampd().emotionalLability()).isEqualTo(80);
        assertThat(entry.frameworks().attachment().style()).isEqualTo("anxious-preoccupied");
        assertThat(entry.frameworks().attachment().anxiety()).isEqualTo(0.75);
        assertThat(entry.frameworks().locusOfControl().general()).isEqualTo("internal");
        assertThat(entry.frameworks().locusOfControl().domains()).containsEntry("art", "internal");
    }

    @Test
    void deserializesArchetypes() throws Exception {
        String json = """
            {
                "id": "pp-2",
                "entity_ref": "Frida Kahlo",
                "archetypes": [
                    {"rank": 1, "family": "creator", "sub_archetype": "artist",
                     "adjectives": ["expressive", "authentic"], "evidence": "created from truth", "confidence": 0.9},
                    {"rank": 2, "family": "rebel", "sub_archetype": "activist",
                     "adjectives": ["cause-driven"], "evidence": "political activism", "confidence": 0.85}
                ]
            }
            """;
        var entry = MAPPER.readValue(json, PersonalityProfileEntry.class);
        assertThat(entry.archetypes()).hasSize(2);
        assertThat(entry.archetypes().getFirst().subArchetype()).isEqualTo("artist");
        assertThat(entry.archetypes().getFirst().adjectives()).containsExactly("expressive", "authentic");
        assertThat(entry.archetypes().get(1).family()).isEqualTo("rebel");
    }

    @Test
    void deserializesDisposition() throws Exception {
        String json = """
            {
                "id": "pp-3",
                "entity_ref": "Frida Kahlo",
                "disposition": {
                    "socialOrient": {"score": 0.7, "term": "cooperative", "justification": "socially engaged"},
                    "ruleFollowing": {"score": 0.25, "term": "flexible", "justification": "defied rules"},
                    "riskAppetite": {"score": 0.75, "term": "bold", "justification": "risk-taking"},
                    "autonomy": {"score": 0.8, "term": "autonomous", "justification": "self-directed"},
                    "conflictMode": {"score": 0.8, "term": "competing", "justification": "confrontational"}
                }
            }
            """;
        var entry = MAPPER.readValue(json, PersonalityProfileEntry.class);
        assertThat(entry.disposition().socialOrient().term()).isEqualTo("cooperative");
        assertThat(entry.disposition().socialOrient().score()).isEqualTo(0.7);
        assertThat(entry.disposition().ruleFollowing().term()).isEqualTo("flexible");
        assertThat(entry.disposition().autonomy().term()).isEqualTo("autonomous");
    }

    @Test
    void deserializesReconciliation() throws Exception {
        String json = """
            {
                "id": "pp-4",
                "entity_ref": "Frida Kahlo",
                "reconciliation": {
                    "published_sources": ["ISFP (16personalities)"],
                    "disagreements": [
                        {"framework": "mbti", "extracted": "infp", "published": "isfp",
                         "axis": "S/N", "evidence_for_extracted": "Ne evidence",
                         "evidence_for_published": "Se evidence", "resolution": "reconciled as INFP"}
                    ]
                },
                "inconsistencies": ["High extraversion with INTJ"]
            }
            """;
        var entry = MAPPER.readValue(json, PersonalityProfileEntry.class);
        assertThat(entry.reconciliation().publishedSources()).containsExactly("ISFP (16personalities)");
        assertThat(entry.reconciliation().disagreements()).hasSize(1);
        assertThat(entry.reconciliation().disagreements().getFirst().extracted()).isEqualTo("infp");
        assertThat(entry.inconsistencies()).containsExactly("High extraversion with INTJ");
    }

    @Test
    void handlesNullsGracefully() throws Exception {
        String json = """
            {
                "id": "pp-5",
                "entity_ref": "Test Subject"
            }
            """;
        var entry = MAPPER.readValue(json, PersonalityProfileEntry.class);
        assertThat(entry.entityRef()).isEqualTo("Test Subject");
        assertThat(entry.archetypes()).isEmpty();
        assertThat(entry.inconsistencies()).isEmpty();
    }
}

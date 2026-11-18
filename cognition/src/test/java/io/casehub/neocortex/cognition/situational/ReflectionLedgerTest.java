package io.casehub.neocortex.cognition.situational;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class ReflectionLedgerTest {

    private static final Instant NOW = Instant.parse("2026-11-17T10:00:00Z");

    @Test
    void initialLedgerRoundTrips() {
        var ledger = ReflectionLedger.initial(NOW);
        var props = ledger.toProperties();
        var loaded = ReflectionLedger.fromProperties(props, NOW);

        assertThat(loaded.lastReflectionAt()).isEqualTo(NOW);
        assertThat(loaded.lastTransitionAt()).isNull();
        assertThat(loaded.pendingEvolution()).isNull();
        assertThat(loaded.disabled()).isFalse();
        assertThat(loaded.decayedThrough()).isEqualTo(NOW);
        assertThat(loaded.reauthor()).isNull();
        assertThat(loaded.epochFingerprint()).isNull();
        assertThat(loaded.lastTransitionFromLabel()).isNull();
        assertThat(loaded.reversalStreak()).isZero();
        assertThat(loaded.auditOutbox()).isEmpty();
        assertThat(loaded.inert()).isFalse();
    }

    @Test
    void fullLedgerRoundTrips() {
        var pending = new ReflectionLedger.PendingEvolution(
                "intent-1", "TI-NE", "NE-TI", Map.of("key", "val"),
                NOW.plusSeconds(100), Duration.ofDays(30));
        var reauthor = new ReflectionLedger.Reauthor("fp123", NOW.minusSeconds(3600), false);
        var audit = List.of(
                new ReflectionLedger.AuditEntry("a1", null, 2, NOW.plusSeconds(86400)));
        var ledger = new ReflectionLedger(
                NOW, NOW.minusSeconds(7200), pending, true,
                NOW.minusSeconds(600), reauthor, "epoch-fp", "TI-NE",
                3, audit, true);
        var props = ledger.toProperties();
        var loaded = ReflectionLedger.fromProperties(props, NOW);

        assertThat(loaded.lastReflectionAt()).isEqualTo(NOW);
        assertThat(loaded.lastTransitionAt()).isEqualTo(NOW.minusSeconds(7200));
        assertThat(loaded.disabled()).isTrue();
        assertThat(loaded.decayedThrough()).isEqualTo(NOW.minusSeconds(600));
        assertThat(loaded.epochFingerprint()).isEqualTo("epoch-fp");
        assertThat(loaded.lastTransitionFromLabel()).isEqualTo("TI-NE");
        assertThat(loaded.reversalStreak()).isEqualTo(3);
        assertThat(loaded.inert()).isTrue();

        assertThat(loaded.pendingEvolution()).isNotNull();
        assertThat(loaded.pendingEvolution().intentId()).isEqualTo("intent-1");
        assertThat(loaded.pendingEvolution().previousLabel()).isEqualTo("TI-NE");
        assertThat(loaded.pendingEvolution().newLabel()).isEqualTo("NE-TI");
        assertThat(loaded.pendingEvolution().at()).isEqualTo(NOW.plusSeconds(100));
        assertThat(loaded.pendingEvolution().interval()).isEqualTo(Duration.ofDays(30));

        assertThat(loaded.reauthor()).isNotNull();
        assertThat(loaded.reauthor().fingerprint()).isEqualTo("fp123");
        assertThat(loaded.reauthor().since()).isEqualTo(NOW.minusSeconds(3600));
        assertThat(loaded.reauthor().confirmed()).isFalse();

        assertThat(loaded.auditOutbox()).hasSize(1);
        assertThat(loaded.auditOutbox().getFirst().intentId()).isEqualTo("a1");
        assertThat(loaded.auditOutbox().getFirst().attempts()).isEqualTo(2);
    }

    @Test
    void advancedByMovesReflectionTime() {
        var ledger = ReflectionLedger.initial(NOW);
        var advanced = ledger.advancedBy(Duration.ofHours(24));
        assertThat(advanced.lastReflectionAt()).isEqualTo(NOW.plus(Duration.ofHours(24)));
    }

    @Test
    void closeEpochMovesIntentToOutbox() {
        var pending = new ReflectionLedger.PendingEvolution(
                "intent-1", "TI-NE", "NE-TI", Map.of("k", "v"),
                NOW, Duration.ofDays(30));
        var ledger = ReflectionLedger.initial(NOW).withPendingEvolution(pending);
        var closed = ledger.closeEpoch();
        assertThat(closed.pendingEvolution()).isNull();
        assertThat(closed.lastTransitionAt()).isEqualTo(NOW);
        assertThat(closed.lastTransitionFromLabel()).isEqualTo("TI-NE");
        assertThat(closed.auditOutbox()).hasSize(1);
        assertThat(closed.auditOutbox().getFirst().intentId()).isEqualTo("intent-1");
    }

    @Test
    void clampedToRegression() {
        var future = NOW.plusSeconds(7200);
        var ledger = new ReflectionLedger(future, null, null, false,
                future, null, null, null, 0, List.of(), false);
        var clamped = ledger.clampedTo(NOW);
        assertThat(clamped.lastReflectionAt()).isEqualTo(NOW);
        assertThat(clamped.decayedThrough()).isEqualTo(NOW);
    }
}

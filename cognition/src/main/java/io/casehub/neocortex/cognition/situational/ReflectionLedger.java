package io.casehub.neocortex.cognition.situational;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.LinkedHashMap;

public record ReflectionLedger(
        Instant lastReflectionAt,
        Instant lastTransitionAt,
        PendingEvolution pendingEvolution,
        boolean disabled,
        Instant decayedThrough,
        Reauthor reauthor,
        String epochFingerprint,
        String lastTransitionFromLabel,
        int reversalStreak,
        List<AuditEntry> auditOutbox,
        boolean inert
) {
    public ReflectionLedger {
        if (lastReflectionAt == null) throw new IllegalArgumentException("lastReflectionAt must not be null");
        auditOutbox = auditOutbox != null ? List.copyOf(auditOutbox) : List.of();
    }

    public static ReflectionLedger initial(Instant now) {
        return new ReflectionLedger(now, null, null, false, now, null, null, null, 0, List.of(), false);
    }

    public ReflectionLedger advancedBy(Duration period) {
        return new ReflectionLedger(lastReflectionAt.plus(period), lastTransitionAt, pendingEvolution,
                disabled, decayedThrough, reauthor, epochFingerprint, lastTransitionFromLabel,
                reversalStreak, auditOutbox, inert);
    }

    public ReflectionLedger withDisabled(boolean d) {
        return new ReflectionLedger(lastReflectionAt, lastTransitionAt, pendingEvolution,
                d, decayedThrough, reauthor, epochFingerprint, lastTransitionFromLabel,
                reversalStreak, auditOutbox, inert);
    }

    public ReflectionLedger withDecayedThrough(Instant dt) {
        return new ReflectionLedger(lastReflectionAt, lastTransitionAt, pendingEvolution,
                disabled, dt, reauthor, epochFingerprint, lastTransitionFromLabel,
                reversalStreak, auditOutbox, inert);
    }

    public ReflectionLedger withEpochFingerprint(String fp) {
        return new ReflectionLedger(lastReflectionAt, lastTransitionAt, pendingEvolution,
                disabled, decayedThrough, reauthor, fp, lastTransitionFromLabel,
                reversalStreak, auditOutbox, inert);
    }

    public ReflectionLedger withReauthor(Reauthor r) {
        return new ReflectionLedger(lastReflectionAt, lastTransitionAt, pendingEvolution,
                disabled, decayedThrough, r, epochFingerprint, lastTransitionFromLabel,
                reversalStreak, auditOutbox, inert);
    }

    public ReflectionLedger withPendingEvolution(PendingEvolution pe) {
        return new ReflectionLedger(lastReflectionAt, lastTransitionAt, pe,
                disabled, decayedThrough, reauthor, epochFingerprint, lastTransitionFromLabel,
                reversalStreak, auditOutbox, inert);
    }

    public ReflectionLedger withLastTransitionAt(Instant at) {
        return new ReflectionLedger(lastReflectionAt, at, pendingEvolution,
                disabled, decayedThrough, reauthor, epochFingerprint, lastTransitionFromLabel,
                reversalStreak, auditOutbox, inert);
    }

    public ReflectionLedger withInert(boolean i) {
        return new ReflectionLedger(lastReflectionAt, lastTransitionAt, pendingEvolution,
                disabled, decayedThrough, reauthor, epochFingerprint, lastTransitionFromLabel,
                reversalStreak, auditOutbox, i);
    }

    public ReflectionLedger withReversalStreak(int rs) {
        return new ReflectionLedger(lastReflectionAt, lastTransitionAt, pendingEvolution,
                disabled, decayedThrough, reauthor, epochFingerprint, lastTransitionFromLabel,
                rs, auditOutbox, inert);
    }

    public ReflectionLedger withLastTransitionFromLabel(String label) {
        return new ReflectionLedger(lastReflectionAt, lastTransitionAt, pendingEvolution,
                disabled, decayedThrough, reauthor, epochFingerprint, label,
                reversalStreak, auditOutbox, inert);
    }

    public ReflectionLedger closeEpoch() {
        if (pendingEvolution == null) return this;
        var entry = new AuditEntry(pendingEvolution.intentId(), pendingEvolution.caseFeatures(), 0, Instant.EPOCH);
        var newOutbox = new java.util.ArrayList<>(auditOutbox);
        newOutbox.add(entry);
        return new ReflectionLedger(lastReflectionAt, pendingEvolution.at(), null,
                disabled, decayedThrough, reauthor, epochFingerprint,
                pendingEvolution.previousLabel(), reversalStreak, newOutbox, inert);
    }

    public ReflectionLedger clampedTo(Instant now) {
        var clampedRef = lastReflectionAt.isAfter(now) ? now : lastReflectionAt;
        var clampedDecay = decayedThrough != null && decayedThrough.isAfter(now) ? now : decayedThrough;
        return new ReflectionLedger(clampedRef, lastTransitionAt, pendingEvolution,
                disabled, clampedDecay, reauthor, epochFingerprint, lastTransitionFromLabel,
                reversalStreak, auditOutbox, inert);
    }

    public Map<String, String> toProperties() {
        var props = new LinkedHashMap<String, String>();
        props.put("jpaf.lastReflectionAt", lastReflectionAt.toString());
        if (lastTransitionAt != null) props.put("jpaf.lastTransitionAt", lastTransitionAt.toString());
        if (disabled) props.put("jpaf.disabled", "true");
        if (decayedThrough != null) props.put("jpaf.decayedThrough", decayedThrough.toString());
        if (epochFingerprint != null) props.put("jpaf.epochFingerprint", epochFingerprint);
        if (lastTransitionFromLabel != null) props.put("jpaf.lastTransitionFromLabel", lastTransitionFromLabel);
        if (reversalStreak != 0) props.put("jpaf.reversalStreak", String.valueOf(reversalStreak));
        if (inert) props.put("jpaf.inert", "true");
        if (pendingEvolution != null) {
            props.put("jpaf.pending.intentId", pendingEvolution.intentId());
            props.put("jpaf.pending.previousLabel", pendingEvolution.previousLabel());
            props.put("jpaf.pending.newLabel", pendingEvolution.newLabel());
            props.put("jpaf.pending.at", pendingEvolution.at().toString());
            if (pendingEvolution.interval() != null) props.put("jpaf.pending.interval", pendingEvolution.interval().toString());
        }
        if (reauthor != null) {
            props.put("jpaf.reauthor.fingerprint", reauthor.fingerprint());
            props.put("jpaf.reauthor.since", reauthor.since().toString());
            props.put("jpaf.reauthor.confirmed", String.valueOf(reauthor.confirmed()));
        }
        int idx = 0;
        for (var entry : auditOutbox) {
            var prefix = "jpaf.audit." + idx + ".";
            props.put(prefix + "intentId", entry.intentId());
            props.put(prefix + "attempts", String.valueOf(entry.attempts()));
            props.put(prefix + "nextAttemptAt", entry.nextAttemptAt().toString());
            idx++;
        }
        if (!auditOutbox.isEmpty()) props.put("jpaf.audit.count", String.valueOf(auditOutbox.size()));
        return Map.copyOf(props);
    }

    public static ReflectionLedger fromProperties(Map<String, String> props, Instant fallbackNow) {
        var lastReflectionAt = parseInstant(props.get("jpaf.lastReflectionAt"), fallbackNow);
        var lastTransitionAt = parseInstant(props.get("jpaf.lastTransitionAt"), null);
        var disabled = "true".equals(props.get("jpaf.disabled"));
        var decayedThrough = parseInstant(props.get("jpaf.decayedThrough"), lastReflectionAt);
        var epochFingerprint = props.get("jpaf.epochFingerprint");
        var lastTransitionFromLabel = props.get("jpaf.lastTransitionFromLabel");
        var reversalStreak = parseInt(props.get("jpaf.reversalStreak"), 0);
        var inert = "true".equals(props.get("jpaf.inert"));
        PendingEvolution pending = null;
        var pendingIntentId = props.get("jpaf.pending.intentId");
        if (pendingIntentId != null) {
            pending = new PendingEvolution(
                    pendingIntentId,
                    props.getOrDefault("jpaf.pending.previousLabel", ""),
                    props.getOrDefault("jpaf.pending.newLabel", ""),
                    null,
                    parseInstant(props.get("jpaf.pending.at"), fallbackNow),
                    parseDuration(props.get("jpaf.pending.interval")));
        }
        Reauthor reauthor = null;
        var reauthorFp = props.get("jpaf.reauthor.fingerprint");
        if (reauthorFp != null) {
            reauthor = new Reauthor(reauthorFp,
                    parseInstant(props.get("jpaf.reauthor.since"), fallbackNow),
                    "true".equals(props.get("jpaf.reauthor.confirmed")));
        }
        var auditCount = parseInt(props.get("jpaf.audit.count"), 0);
        var outbox = new java.util.ArrayList<AuditEntry>();
        for (int i = 0; i < auditCount; i++) {
            var prefix = "jpaf.audit." + i + ".";
            var intentId = props.get(prefix + "intentId");
            if (intentId != null) {
                outbox.add(new AuditEntry(intentId, null,
                        parseInt(props.get(prefix + "attempts"), 0),
                        parseInstant(props.get(prefix + "nextAttemptAt"), Instant.EPOCH)));
            }
        }
        return new ReflectionLedger(lastReflectionAt, lastTransitionAt, pending,
                disabled, decayedThrough, reauthor, epochFingerprint, lastTransitionFromLabel,
                reversalStreak, outbox, inert);
    }

    private static Instant parseInstant(String v, Instant fallback) {
        if (v == null) return fallback;
        try { return Instant.parse(v); }
        catch (Exception e) { return fallback; }
    }

    private static int parseInt(String v, int fallback) {
        if (v == null) return fallback;
        try { return Integer.parseInt(v); }
        catch (NumberFormatException e) { return fallback; }
    }

    private static Duration parseDuration(String v) {
        if (v == null) return null;
        try { return Duration.parse(v); }
        catch (Exception e) { return null; }
    }

    public record PendingEvolution(String intentId, String previousLabel, String newLabel,
                                   Map<String, String> caseFeatures, Instant at, Duration interval) {}

    public record Reauthor(String fingerprint, Instant since, boolean confirmed) {}

    public record AuditEntry(String intentId, Map<String, String> caseFeatures, int attempts, Instant nextAttemptAt) {}
}

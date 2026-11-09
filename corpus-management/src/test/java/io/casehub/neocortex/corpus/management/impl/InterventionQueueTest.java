package io.casehub.neocortex.corpus.management.impl;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

class InterventionQueueTest {

    private InterventionQueue queue;

    @BeforeEach
    void setUp() {
        queue = new InterventionQueue(Duration.ofMinutes(10));
    }

    @Test
    void submitCreatesEntry() {
        var request = queue.submit("https://example.com", "Login required", null);
        assertThat(request.id()).isNotNull();
        assertThat(request.id()).startsWith("intv-");
        assertThat(request.url()).isEqualTo("https://example.com");
        assertThat(request.reason()).isEqualTo("Login required");
    }

    @Test
    void pendingReturnSubmittedEntries() {
        queue.submit("https://a.com", "CAPTCHA", null);
        queue.submit("https://b.com", "Login", "/tmp/screenshot.png");

        var pending = queue.pending();
        assertThat(pending).hasSize(2);
    }

    @Test
    void resolveRemovesAndReturnsEntry() {
        var submitted = queue.submit("https://example.com", "CAPTCHA", null);

        var resolved = queue.resolve(submitted.id());
        assertThat(resolved).isPresent();
        assertThat(resolved.get().url()).isEqualTo("https://example.com");
        assertThat(resolved.get().reason()).isEqualTo("CAPTCHA");

        assertThat(queue.pending()).isEmpty();
    }

    @Test
    void resolveUnknownIdReturnsEmpty() {
        var resolved = queue.resolve("intv-nonexistent");
        assertThat(resolved).isEmpty();
    }

    @Test
    void expiredEntriesEvictedFromPending() {
        var shortQueue = new InterventionQueue(Duration.ofMillis(1));
        shortQueue.submit("https://example.com", "CAPTCHA", null);

        try { Thread.sleep(10); } catch (InterruptedException ignored) {}

        assertThat(shortQueue.pending()).isEmpty();
    }

    @Test
    void resolveExpiredEntryReturnsEmpty() {
        var shortQueue = new InterventionQueue(Duration.ofMillis(1));
        var submitted = shortQueue.submit("https://example.com", "CAPTCHA", null);

        try { Thread.sleep(10); } catch (InterruptedException ignored) {}

        assertThat(shortQueue.resolve(submitted.id())).isEmpty();
    }

    @Test
    void multipleSubmitsGetUniqueIds() {
        var r1 = queue.submit("https://example.com", "CAPTCHA", null);
        var r2 = queue.submit("https://example.com", "CAPTCHA", null);
        assertThat(r1.id()).isNotEqualTo(r2.id());
    }
}

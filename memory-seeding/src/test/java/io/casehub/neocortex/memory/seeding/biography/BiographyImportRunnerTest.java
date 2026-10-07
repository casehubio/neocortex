package io.casehub.neocortex.memory.seeding.biography;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class BiographyImportRunnerTest {

    @Test
    void handlersInvokedInLayerOrder() {
        var order = new AtomicInteger(0);
        var layer1Handler = new TestHandler(Set.of("cultural-context"), order);
        var layer5Handler = new TestHandler(Set.of("relationship"), order);
        var layer4Handler = new TestHandler(Set.of("place"), order);

        var runner = new BiographyImportRunner(
            List.of(layer5Handler, layer1Handler, layer4Handler));
        runner.run(emptyProfile(), "agent-1", "tenant-1");

        assertThat(layer1Handler.invokedAt).isEqualTo(0);
        assertThat(layer4Handler.invokedAt).isEqualTo(1);
        assertThat(layer5Handler.invokedAt).isEqualTo(2);
    }

    @Test
    void rejectsDoubleSeedingSameAgent() {
        var runner = new BiographyImportRunner(List.of());
        runner.run(emptyProfile(), "agent-1", "tenant-1");
        assertThatThrownBy(() -> runner.run(emptyProfile(), "agent-1", "tenant-1"))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("already has biographical data");
    }

    @Test
    void differentAgentsCanBothBeSeeded() {
        var runner = new BiographyImportRunner(List.of());
        runner.run(emptyProfile(), "agent-1", "tenant-1");
        runner.run(emptyProfile(), "agent-2", "tenant-1");
    }

    private BiographyProfile emptyProfile() {
        return new BiographyProfile("a1", "t1",
            List.of(), List.of(), List.of(), List.of(), List.of(),
            List.of(), List.of(), List.of(), List.of(), List.of());
    }

    static class TestHandler implements BiographyHandler {
        final Set<String> types;
        final AtomicInteger counter;
        int invokedAt;

        TestHandler(Set<String> types, AtomicInteger counter) {
            this.types = types;
            this.counter = counter;
        }

        @Override public Set<String> handledTypes() { return types; }
        @Override public void handle(BiographyProfile p, String a, String t) {
            invokedAt = counter.getAndIncrement();
        }
    }
}

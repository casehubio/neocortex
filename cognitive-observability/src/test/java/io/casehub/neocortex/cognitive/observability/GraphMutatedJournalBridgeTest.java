package io.casehub.neocortex.cognitive.observability;

import io.casehub.neocortex.cognition.journal.CognitiveEntry;
import io.casehub.neocortex.cognition.journal.CognitiveJournal;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class GraphMutatedJournalBridgeTest {

    private static jakarta.enterprise.inject.Instance<CognitiveJournal> wrapJournal(CognitiveJournal journal) {
        return new jakarta.enterprise.inject.Instance<>() {
            @Override
            public CognitiveJournal get()                                                                                                                                    {return journal;}

            @Override
            public boolean isUnsatisfied()                                                                                                                                   {return false;}

            @Override
            public boolean isResolvable()                                                                                                                                    {return true;}

            @Override
            public boolean isAmbiguous()                                                                                                                                     {return false;}

            @Override
            public jakarta.enterprise.inject.Instance<CognitiveJournal> select(java.lang.annotation.Annotation... q)                                                         {return this;}

            @Override
            public <U extends CognitiveJournal> jakarta.enterprise.inject.Instance<U> select(Class<U> s, java.lang.annotation.Annotation... q)                               {return null;}

            @Override
            public <U extends CognitiveJournal> jakarta.enterprise.inject.Instance<U> select(jakarta.enterprise.util.TypeLiteral<U> s, java.lang.annotation.Annotation... q) {return null;}

            @Override
            public void destroy(CognitiveJournal instance)                                                                                                                   {}

            @Override
            public Handle<CognitiveJournal> getHandle()                                                                                                                      {return null;}

            @Override
            public Iterable<Handle<CognitiveJournal>> handles()                                                                                                              {return List.of();}

            @Override
            public java.util.Iterator<CognitiveJournal> iterator()                                                                                                           {return List.of(journal).iterator();}
        };
    }

    @Test
    void shouldBridgeEventWithAgentIdToJournal() {
        List<CognitiveEntry> entries = new ArrayList<>();
        var                  bridge  = new GraphMutatedJournalBridge(wrapJournal(entries::add));

        var mutation = new GraphMutation.SubgraphCreated("sg-1", "test", "concept", Instant.now(), "manual");
        var event    = new GraphMutationRecorded("tenant-1", "agent-42", mutation);

        bridge.onGraphMutation(event);

        assertThat(entries).hasSize(1);
        var entry = (CognitiveEntry.GraphMutated) entries.getFirst();
        assertThat(entry.tenantId()).isEqualTo("tenant-1");
        assertThat(entry.agentId()).isEqualTo("agent-42");
        assertThat(entry.mutation()).isSameAs(mutation);
        assertThat(entry.timestamp()).isEqualTo(mutation.timestamp());
        assertThat(entry.eventRef().toString()).startsWith("event:");
    }

    @Test
    void shouldSkipEventWithNullAgentId() {
        List<CognitiveEntry> entries = new ArrayList<>();
        var                  bridge  = new GraphMutatedJournalBridge(wrapJournal(entries::add));

        var mutation = new GraphMutation.SubgraphCreated("sg-1", "test", "concept", Instant.now(), "manual");
        var event    = new GraphMutationRecorded("tenant-1", null, mutation);

        bridge.onGraphMutation(event);

        assertThat(entries).isEmpty();
    }

    @Test
    void shouldDeriveConsolidationEventRef() {
        List<CognitiveEntry> entries = new ArrayList<>();
        var                  bridge  = new GraphMutatedJournalBridge(wrapJournal(entries::add));

        var mutation = new GraphMutation.NodeErased("n-1", "sg-1", 0, Instant.now(), "consolidation:MergeDetectionPhase");
        var event    = new GraphMutationRecorded("tenant-1", "_system", mutation);

        bridge.onGraphMutation(event);

        assertThat(entries).hasSize(1);
        var entry = (CognitiveEntry.GraphMutated) entries.getFirst();
        assertThat(entry.eventRef().toString()).startsWith("consolidation:");
    }

    @Test
    void shouldSkipWhenJournalUnsatisfied() {
        jakarta.enterprise.inject.Instance<CognitiveJournal> unsatisfied = new jakarta.enterprise.inject.Instance<>() {
            @Override
            public CognitiveJournal get()                                                                                                                                    {throw new IllegalStateException();}

            @Override
            public boolean isUnsatisfied()                                                                                                                                   {return true;}

            @Override
            public boolean isResolvable()                                                                                                                                    {return false;}

            @Override
            public boolean isAmbiguous()                                                                                                                                     {return false;}

            @Override
            public jakarta.enterprise.inject.Instance<CognitiveJournal> select(java.lang.annotation.Annotation... q)                                                         {return this;}

            @Override
            public <U extends CognitiveJournal> jakarta.enterprise.inject.Instance<U> select(Class<U> s, java.lang.annotation.Annotation... q)                               {return null;}

            @Override
            public <U extends CognitiveJournal> jakarta.enterprise.inject.Instance<U> select(jakarta.enterprise.util.TypeLiteral<U> s, java.lang.annotation.Annotation... q) {return null;}

            @Override
            public void destroy(CognitiveJournal instance)                                                                                                                   {}

            @Override
            public Handle<CognitiveJournal> getHandle()                                                                                                                      {return null;}

            @Override
            public Iterable<Handle<CognitiveJournal>> handles()                                                                                                              {return List.of();}

            @Override
            public java.util.Iterator<CognitiveJournal> iterator()                                                                                                           {return java.util.Collections.emptyIterator();}
        };
        var bridge = new GraphMutatedJournalBridge(unsatisfied);

        var mutation = new GraphMutation.SubgraphCreated("sg-1", "test", "concept", Instant.now(), "manual");
        var event    = new GraphMutationRecorded("tenant-1", "agent-1", mutation);

        bridge.onGraphMutation(event);
        // No exception, no recording — just silently skips
    }
}

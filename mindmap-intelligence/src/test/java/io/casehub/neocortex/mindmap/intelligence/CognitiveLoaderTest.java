/*
 * Copyright 2026-Present The Case Hub Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.casehub.neocortex.mindmap.intelligence;

import io.casehub.neocortex.cognitive.index.CognitiveDefaults;
import io.casehub.neocortex.mindmap.EdgeTypeDefinition;
import io.casehub.neocortex.mindmap.MindMapVocabulary;
import io.casehub.neocortex.mindmap.VocabularyConflictException;
import io.casehub.neocortex.mindmap.inmem.InMemoryMindMapStore;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CognitiveLoaderTest {

    @Test
    void registersVocabularyFromProfiles() {
        var vocab = new MindMapVocabulary(List.of(
                new EdgeTypeDefinition("knows", Set.of("knows-about"), null)));
        var defaults = new CognitiveDefaults(
                "alice", null, null, null, null, null, vocab, Map.of(), null, null);
        var store = new InMemoryMindMapStore();

        var loader = new CognitiveLoader(store, List.of(defaults));
        loader.init();

        // Vocabulary was registered — conflicting alias should throw
        var conflict = new MindMapVocabulary(List.of(
                new EdgeTypeDefinition("other", Set.of("knows-about"), null)));
        assertThatThrownBy(() -> store.registerVocabulary(conflict))
                .isInstanceOf(VocabularyConflictException.class);
    }

    @Test
    void nullVocabulary_skippedGracefully() {
        var defaults = new CognitiveDefaults(
                "bob", null, null, null, null, null, null, Map.of(), null, null);
        var store = new InMemoryMindMapStore();

        var loader = new CognitiveLoader(store, List.of(defaults));
        loader.init();

        // No vocabulary registered — should accept any registration
        store.registerVocabulary(new MindMapVocabulary(List.of(
                new EdgeTypeDefinition("anything", Set.of(), null))));
    }

    @Test
    void emptyProfiles_noOp() {
        var store  = new InMemoryMindMapStore();
        var loader = new CognitiveLoader(store, List.of());
        loader.init();
    }

    @Test
    void multipleProfiles_allVocabulariesRegistered() {
        var vocab1 = new MindMapVocabulary(List.of(
                new EdgeTypeDefinition("knows", Set.of("knows-about"), null)));
        var vocab2 = new MindMapVocabulary(List.of(
                new EdgeTypeDefinition("works-at", Set.of("employed-by"), null)));
        var alice = new CognitiveDefaults(
                "alice", null, null, null, null, null, vocab1, Map.of(), null, null);
        var carol = new CognitiveDefaults(
                "carol", null, null, null, null, null, vocab2, Map.of(), null, null);
        var store = new InMemoryMindMapStore();

        var loader = new CognitiveLoader(store, List.of(alice, carol));
        loader.init();

        // Both vocabularies registered
        var conflict1 = new MindMapVocabulary(List.of(
                new EdgeTypeDefinition("x", Set.of("knows-about"), null)));
        assertThatThrownBy(() -> store.registerVocabulary(conflict1))
                .isInstanceOf(VocabularyConflictException.class);
        var conflict2 = new MindMapVocabulary(List.of(
                new EdgeTypeDefinition("y", Set.of("employed-by"), null)));
        assertThatThrownBy(() -> store.registerVocabulary(conflict2))
                .isInstanceOf(VocabularyConflictException.class);
    }
}

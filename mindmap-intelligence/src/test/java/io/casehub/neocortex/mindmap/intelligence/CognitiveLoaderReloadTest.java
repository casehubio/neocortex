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
import io.casehub.neocortex.cognitive.index.CognitiveProfilesReloaded;
import io.casehub.neocortex.mindmap.EdgeTypeDefinition;
import io.casehub.neocortex.mindmap.MindMapVocabulary;
import io.casehub.neocortex.mindmap.VocabularyConflictException;
import io.casehub.neocortex.mindmap.inmem.InMemoryMindMapStore;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CognitiveLoaderReloadTest {

    @Test
    void onProfilesReloaded_registersVocabularyForAllProfiles() {
        var store  = new InMemoryMindMapStore();
        var loader = new CognitiveLoader(store, null, List.of());

        var vocab = new MindMapVocabulary(List.of(
                new EdgeTypeDefinition("likes", Set.of("is-fond-of"), null)));
        var profile = CognitiveDefaults.empty("agent-1").withVocabulary(vocab);

        loader.onProfilesReloaded(new CognitiveProfilesReloaded(List.of(profile)));

        var conflict = new MindMapVocabulary(List.of(
                new EdgeTypeDefinition("other", Set.of("is-fond-of"), null)));
        assertThatThrownBy(() -> store.registerVocabulary(conflict))
                .isInstanceOf(VocabularyConflictException.class);
    }

    @Test
    void onProfilesReloaded_skipsProfilesWithoutVocabulary() {
        var store  = new InMemoryMindMapStore();
        var loader = new CognitiveLoader(store, null, List.of());

        var profile = CognitiveDefaults.empty("agent-no-vocab");

        loader.onProfilesReloaded(new CognitiveProfilesReloaded(List.of(profile)));

        store.registerVocabulary(new MindMapVocabulary(List.of(
                new EdgeTypeDefinition("anything", Set.of(), null))));
    }

    @Test
    void onProfilesReloaded_noStore_doesNotThrow() {
        var loader = new CognitiveLoader(null, null, List.of());

        var vocab = new MindMapVocabulary(List.of(
                new EdgeTypeDefinition("likes", Set.of(), null)));
        var profile = CognitiveDefaults.empty("agent-1").withVocabulary(vocab);

        loader.onProfilesReloaded(new CognitiveProfilesReloaded(List.of(profile)));
    }
}

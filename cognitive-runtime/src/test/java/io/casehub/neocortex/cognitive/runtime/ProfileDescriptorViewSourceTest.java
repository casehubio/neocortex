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
package io.casehub.neocortex.cognitive.runtime;

import io.casehub.neocortex.cognitive.index.CognitiveDefaults;
import io.casehub.neocortex.cognitive.index.CognitiveDefaultsRegistry;
import io.casehub.neocortex.cognitive.index.DescriptorView;
import io.casehub.neocortex.cognitive.index.DispositionAxes;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ProfileDescriptorViewSourceTest {

    @Test
    void findsAgentByIdAndTenant() {
        var descriptor = DescriptorView.of("mentor", new DispositionAxes("facilitative", "strict", "conservative", "autonomous", "collaborating"), List.of(), List.of());
        var profile = CognitiveDefaults.empty("mentor").withDescriptor(descriptor);
        var registry = CognitiveDefaultsRegistry.forTesting(profile);
        var source = new ProfileDescriptorViewSource(registry);

        var result = source.find("any-tenant", "mentor");
        assertTrue(result.isPresent());
        assertEquals("strict", result.get().disposition().ruleFollowing());
    }

    @Test
    void returnsEmptyForUnknownAgent() {
        var registry = CognitiveDefaultsRegistry.forTesting();
        var source = new ProfileDescriptorViewSource(registry);

        var result = source.find("any-tenant", "nobody");
        assertTrue(result.isEmpty());
    }

    @Test
    void filtersByTenantId() {
        var descriptor = DescriptorView.of("scoped", null, List.of(), List.of());
        var profile = new CognitiveDefaults("scoped", "tenant-a", null, descriptor, null, null, null, null, null);
        var registry = CognitiveDefaultsRegistry.forTesting(profile);
        var source = new ProfileDescriptorViewSource(registry);

        assertTrue(source.find("tenant-a", "scoped").isPresent());
        assertTrue(source.find("tenant-b", "scoped").isEmpty());
    }

    @Test
    void nullTenantIdAvailableToAllTenants() {
        var descriptor = DescriptorView.of("global", null, List.of(), List.of());
        var profile = CognitiveDefaults.empty("global").withDescriptor(descriptor);
        var registry = CognitiveDefaultsRegistry.forTesting(profile);
        var source = new ProfileDescriptorViewSource(registry);

        assertTrue(source.find("tenant-a", "global").isPresent());
        assertTrue(source.find("tenant-b", "global").isPresent());
    }

    @Test
    void agentIdsFilteredByTenant() {
        var globalProfile = CognitiveDefaults.empty("global-agent");
        var scopedProfile = new CognitiveDefaults("scoped-agent", "tenant-x", null, null, null, null, null, null, null);
        var registry = CognitiveDefaultsRegistry.forTesting(globalProfile, scopedProfile);
        var source = new ProfileDescriptorViewSource(registry);

        var ids = source.agentIds("tenant-x");
        assertTrue(ids.contains("global-agent"));
        assertTrue(ids.contains("scoped-agent"));

        var otherIds = source.agentIds("tenant-y");
        assertTrue(otherIds.contains("global-agent"));
        assertFalse(otherIds.contains("scoped-agent"));
    }

    @Test
    void returnsNullDescriptorWhenProfileHasNoDescriptor() {
        var profile = CognitiveDefaults.empty("bare-agent");
        var registry = CognitiveDefaultsRegistry.forTesting(profile);
        var source = new ProfileDescriptorViewSource(registry);

        var result = source.find("any-tenant", "bare-agent");
        assertTrue(result.isEmpty());
    }
}

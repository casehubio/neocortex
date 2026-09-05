package io.casehub.neocortex.memory.inmem;

import io.casehub.neocortex.memory.MemoryDomain;

record BucketKey(String tenantId, String subjectId, MemoryDomain domain) {}

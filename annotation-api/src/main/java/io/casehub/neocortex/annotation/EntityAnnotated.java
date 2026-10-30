package io.casehub.neocortex.annotation;

import java.util.List;

public record EntityAnnotated(List<EntityAnnotation> annotations, String tenantId) {}

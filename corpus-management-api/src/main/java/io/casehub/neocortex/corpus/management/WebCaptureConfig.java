package io.casehub.neocortex.corpus.management;

public record WebCaptureConfig(int defaultTtlDays, int interventionTimeoutMinutes) {

    public static final WebCaptureConfig DEFAULT = new WebCaptureConfig(30, 10);

    public WebCaptureConfig {
        if (defaultTtlDays <= 0) throw new IllegalArgumentException("defaultTtlDays must be positive");
        if (interventionTimeoutMinutes <= 0) throw new IllegalArgumentException("interventionTimeoutMinutes must be positive");
    }
}

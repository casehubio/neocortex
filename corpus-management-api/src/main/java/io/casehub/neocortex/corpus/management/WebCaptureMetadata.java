package io.casehub.neocortex.corpus.management;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

public final class WebCaptureMetadata {

    public static final String DOMAIN = "web-cache";

    public static final String URL = "url";
    public static final String FETCHED_AT = "fetched_at";
    public static final String HTTP_STATUS = "http_status";
    public static final String CONTENT_TYPE = "content_type";
    public static final String TTL_DAYS = "ttl_days";
    public static final String CAPTURE_METHOD = "capture_method";

    public static final int DEFAULT_TTL_DAYS = 30;

    public static Map<String, Object> build(String url, int httpStatus, String contentType,
                                            String captureMethod, int ttlDays) {
        var metadata = new LinkedHashMap<String, Object>();
        metadata.put("domain", DOMAIN);
        metadata.put(URL, url);
        metadata.put(FETCHED_AT, Instant.now().toString());
        metadata.put(HTTP_STATUS, httpStatus);
        metadata.put(CONTENT_TYPE, contentType);
        metadata.put(CAPTURE_METHOD, captureMethod);
        metadata.put(TTL_DAYS, ttlDays);
        return metadata;
    }

    public static Map<String, Object> build(String url, int httpStatus, String contentType,
                                            String captureMethod) {
        return build(url, httpStatus, contentType, captureMethod, DEFAULT_TTL_DAYS);
    }

    private WebCaptureMetadata() {}
}

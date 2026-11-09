package io.casehub.neocortex.corpus.management;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class WebCaptureMetadataTest {

    @Test
    void buildIncludesAllFields() {
        Map<String, Object> meta = WebCaptureMetadata.build(
                "https://example.com", 200, "text/html", "playwright", 14);

        assertThat(meta).containsEntry("domain", "web-cache");
        assertThat(meta).containsEntry(WebCaptureMetadata.URL, "https://example.com");
        assertThat(meta).containsEntry(WebCaptureMetadata.HTTP_STATUS, 200);
        assertThat(meta).containsEntry(WebCaptureMetadata.CONTENT_TYPE, "text/html");
        assertThat(meta).containsEntry(WebCaptureMetadata.CAPTURE_METHOD, "playwright");
        assertThat(meta).containsEntry(WebCaptureMetadata.TTL_DAYS, 14);
        assertThat(meta).containsKey(WebCaptureMetadata.FETCHED_AT);
    }

    @Test
    void buildDefaultTtl() {
        Map<String, Object> meta = WebCaptureMetadata.build(
                "https://example.com", 200, "text/html", "playwright");

        assertThat(meta).containsEntry(WebCaptureMetadata.TTL_DAYS, 30);
    }
}

package io.casehub.neocortex.corpus.management.impl;

import io.casehub.neocortex.corpus.management.ImageDescriber;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import static org.assertj.core.api.Assertions.assertThat;

class WebImageProcessorTest {

    @Test
    void returnsEmptyForHtmlWithoutImages() {
        var processor = new WebImageProcessor(null);
        var result = processor.process("<html><body><p>No images here</p></body></html>",
                                        "https://example.com/page");
        assertThat(result.images()).isEmpty();
    }

    @Test
    void extractsImageUrlsFromHtml() {
        var fetched = new ConcurrentHashMap<String, byte[]>();
        var processor = testProcessor(fetched, null);

        String html = "<html><body>"
            + "<img src=\"https://example.com/img1.png\" alt=\"First\">"
            + "<img src=\"https://example.com/img2.jpg\" alt=\"Second\">"
            + "</body></html>";

        var result = processor.process(html, "https://example.com/page");

        assertThat(result.images()).hasSize(2);
        assertThat(result.images().get(0).sourceUrl()).isEqualTo("https://example.com/img1.png");
        assertThat(result.images().get(0).altText()).isEqualTo("First");
        assertThat(result.images().get(0).mimeType()).isEqualTo("image/png");
        assertThat(result.images().get(1).sourceUrl()).isEqualTo("https://example.com/img2.jpg");
        assertThat(result.images().get(1).altText()).isEqualTo("Second");
        assertThat(result.images().get(1).mimeType()).isEqualTo("image/jpeg");
    }

    @Test
    void resolvesRelativeUrls() {
        var fetched = new ConcurrentHashMap<String, byte[]>();
        var processor = testProcessor(fetched, null);

        String html = "<html><body>"
            + "<img src=\"/images/photo.png\" alt=\"Photo\">"
            + "<img src=\"relative/path.jpg\" alt=\"Relative\">"
            + "</body></html>";

        var result = processor.process(html, "https://example.com/articles/page.html");

        assertThat(result.images()).hasSize(2);
        assertThat(result.images().get(0).sourceUrl()).isEqualTo("https://example.com/images/photo.png");
        assertThat(result.images().get(1).sourceUrl()).isEqualTo("https://example.com/articles/relative/path.jpg");
    }

    @Test
    void callsImageDescriberWhenAvailable() {
        var fetched = new ConcurrentHashMap<String, byte[]>();
        ImageDescriber describer = (bytes, mime) -> "A beautiful landscape";
        var processor = testProcessor(fetched, describer);

        String html = "<html><body><img src=\"https://example.com/img.png\" alt=\"Scene\"></body></html>";
        var result = processor.process(html, "https://example.com/page");

        assertThat(result.images()).hasSize(1);
        assertThat(result.images().get(0).description()).isEqualTo("A beautiful landscape");
    }

    @Test
    void skipsDescriptionWhenNoDescriber() {
        var fetched = new ConcurrentHashMap<String, byte[]>();
        var processor = testProcessor(fetched, null);

        String html = "<html><body><img src=\"https://example.com/img.png\" alt=\"Scene\"></body></html>";
        var result = processor.process(html, "https://example.com/page");

        assertThat(result.images()).hasSize(1);
        assertThat(result.images().get(0).description()).isNull();
    }

    @Test
    void continuesWhenDescriberThrows() {
        var fetched = new ConcurrentHashMap<String, byte[]>();
        ImageDescriber describer = (bytes, mime) -> { throw new RuntimeException("LLM failed"); };
        var processor = testProcessor(fetched, describer);

        String html = "<html><body><img src=\"https://example.com/img.png\" alt=\"Scene\"></body></html>";
        var result = processor.process(html, "https://example.com/page");

        assertThat(result.images()).hasSize(1);
        assertThat(result.images().get(0).description()).isNull();
    }

    @Test
    void skipsImageWhenFetchFails() {
        var processor = new WebImageProcessor(null) {
            @Override
            byte[] fetchImageBytes(String url) {
                if (url.contains("broken")) return null;
                return new byte[]{1, 2, 3};
            }
        };

        String html = "<html><body>"
            + "<img src=\"https://example.com/broken.png\" alt=\"Broken\">"
            + "<img src=\"https://example.com/good.png\" alt=\"Good\">"
            + "</body></html>";

        var result = processor.process(html, "https://example.com/page");

        assertThat(result.images()).hasSize(1);
        assertThat(result.images().get(0).altText()).isEqualTo("Good");
    }

    @Test
    void skipsDataUrls() {
        var fetched = new ConcurrentHashMap<String, byte[]>();
        var processor = testProcessor(fetched, null);

        String html = "<html><body>"
            + "<img src=\"data:image/png;base64,abc\" alt=\"Inline\">"
            + "<img src=\"https://example.com/real.png\" alt=\"Real\">"
            + "</body></html>";

        var result = processor.process(html, "https://example.com/page");

        assertThat(result.images()).hasSize(1);
        assertThat(result.images().get(0).altText()).isEqualTo("Real");
    }

    @Test
    void enrichMarkdownAddsDescribeAttribute() {
        var processor = new WebImageProcessor(null);
        var images = List.of(
            new CapturedImage("https://example.com/img.png", "Diagram",
                              new byte[]{1}, "image/png", "A layered architecture diagram")
        );
        var result = new ImageProcessingResult(images);

        String markdown = "#@ page: 1\n#@ panel: main\n"
            + "#@ figure: caption=\"Diagram\"\n[Diagram]\n"
            + "Some text after\n";

        String enriched = processor.enrichMarkdown(markdown, result);

        assertThat(enriched).contains(
            "#@ figure: caption=\"Diagram\" describe=\"A layered architecture diagram\"");
        assertThat(enriched).contains("[Diagram]");
        assertThat(enriched).contains("Some text after");
    }

    @Test
    void enrichMarkdownMatchesByPositionOrder() {
        var processor = new WebImageProcessor(null);
        var images = List.of(
            new CapturedImage("url1", "First", new byte[]{1}, "image/png", "Desc of first"),
            new CapturedImage("url2", "Second", new byte[]{2}, "image/png", "Desc of second")
        );
        var result = new ImageProcessingResult(images);

        String markdown = "#@ figure: caption=\"First\"\n[First]\n"
            + "#@ figure: caption=\"Second\"\n[Second]\n";

        String enriched = processor.enrichMarkdown(markdown, result);

        assertThat(enriched).contains("caption=\"First\" describe=\"Desc of first\"");
        assertThat(enriched).contains("caption=\"Second\" describe=\"Desc of second\"");
    }

    @Test
    void enrichMarkdownLeavesUnmatchedFiguresUnchanged() {
        var processor = new WebImageProcessor(null);
        var result = ImageProcessingResult.EMPTY;

        String markdown = "#@ figure: caption=\"Orphan\"\n[Orphan]\n";
        String enriched = processor.enrichMarkdown(markdown, result);

        assertThat(enriched).isEqualTo(markdown);
    }

    @Test
    void enrichMarkdownSkipsNullDescription() {
        var processor = new WebImageProcessor(null);
        var images = List.of(
            new CapturedImage("url", "Img", new byte[]{1}, "image/png", null)
        );
        var result = new ImageProcessingResult(images);

        String markdown = "#@ figure: caption=\"Img\"\n[Img]\n";
        String enriched = processor.enrichMarkdown(markdown, result);

        assertThat(enriched).doesNotContain("describe=");
    }

    private WebImageProcessor testProcessor(Map<String, byte[]> fetched, ImageDescriber describer) {
        return new WebImageProcessor(describer) {
            @Override
            byte[] fetchImageBytes(String url) {
                byte[] bytes = new byte[]{1, 2, 3};
                fetched.put(url, bytes);
                return bytes;
            }
        };
    }
}

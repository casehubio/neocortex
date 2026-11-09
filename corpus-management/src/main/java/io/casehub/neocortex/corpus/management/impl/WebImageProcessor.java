package io.casehub.neocortex.corpus.management.impl;

import io.casehub.neocortex.corpus.management.ImageDescriber;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

public class WebImageProcessor {

    private static final Logger LOG = Logger.getLogger(WebImageProcessor.class.getName());
    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(10);
    private static final Duration READ_TIMEOUT = Duration.ofSeconds(30);

    private final ImageDescriber imageDescriber;
    private final HttpClient httpClient;

    public WebImageProcessor(ImageDescriber imageDescriber) {
        this.imageDescriber = imageDescriber;
        this.httpClient = HttpClient.newBuilder()
            .followRedirects(HttpClient.Redirect.NORMAL)
            .connectTimeout(CONNECT_TIMEOUT)
            .build();
    }

    public ImageProcessingResult process(String html, String sourceUrl) {
        Document doc = Jsoup.parse(html, sourceUrl);
        var imgElements = doc.select("img[src]");

        if (imgElements.isEmpty()) {
            return ImageProcessingResult.EMPTY;
        }

        List<CapturedImage> captured = new ArrayList<>();

        for (Element img : imgElements) {
            String src = img.absUrl("src");
            if (src.isBlank() || src.startsWith("data:")) {
                continue;
            }

            String alt = img.attr("alt");

            byte[] bytes = fetchImageBytes(src);
            if (bytes == null) {
                continue;
            }

            String mimeType = detectMimeType(src);
            String description = describe(bytes, mimeType);

            captured.add(new CapturedImage(src, alt, bytes, mimeType, description));
        }

        return new ImageProcessingResult(captured);
    }

    public String enrichMarkdown(String markdown, ImageProcessingResult result) {
        if (result.images().isEmpty()) {
            return markdown;
        }

        String[] lines = markdown.split("\n", -1);
        var sb = new StringBuilder();
        int imageIndex = 0;

        for (int i = 0; i < lines.length; i++) {
            String line = lines[i];
            if (line.startsWith("#@ figure:") && imageIndex < result.images().size()) {
                CapturedImage image = result.images().get(imageIndex);
                imageIndex++;

                if (image.description() != null && !image.description().isBlank()) {
                    String escaped = image.description()
                        .replace("\\", "\\\\")
                        .replace("\"", "\\\"")
                        .replace("\n", " ");
                    sb.append(line).append(" describe=\"").append(escaped).append("\"");
                } else {
                    sb.append(line);
                }
            } else {
                sb.append(line);
            }
            if (i < lines.length - 1) {
                sb.append('\n');
            }
        }

        return sb.toString();
    }

    byte[] fetchImageBytes(String url) {
        try {
            var request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(READ_TIMEOUT)
                .GET()
                .build();
            var response = httpClient.send(request, HttpResponse.BodyHandlers.ofByteArray());
            if (response.statusCode() >= 400) {
                LOG.log(Level.WARNING, "Image fetch returned {0} for {1}",
                        new Object[]{response.statusCode(), url});
                return null;
            }
            return response.body();
        } catch (Exception e) {
            LOG.log(Level.WARNING, "Failed to fetch image: " + url, e);
            return null;
        }
    }

    private String describe(byte[] bytes, String mimeType) {
        if (imageDescriber == null) {
            return null;
        }
        try {
            return imageDescriber.describe(bytes, mimeType);
        } catch (Exception e) {
            LOG.log(Level.WARNING, "ImageDescriber failed", e);
            return null;
        }
    }

    private static String detectMimeType(String url) {
        String path = url;
        int query = url.indexOf('?');
        if (query >= 0) path = url.substring(0, query);
        int fragment = path.indexOf('#');
        if (fragment >= 0) path = path.substring(0, fragment);

        String lower = path.toLowerCase();
        if (lower.endsWith(".jpg") || lower.endsWith(".jpeg")) return "image/jpeg";
        if (lower.endsWith(".gif")) return "image/gif";
        if (lower.endsWith(".svg")) return "image/svg+xml";
        if (lower.endsWith(".webp")) return "image/webp";
        if (lower.endsWith(".bmp")) return "image/bmp";
        if (lower.endsWith(".tiff") || lower.endsWith(".tif")) return "image/tiff";
        return "image/png";
    }
}

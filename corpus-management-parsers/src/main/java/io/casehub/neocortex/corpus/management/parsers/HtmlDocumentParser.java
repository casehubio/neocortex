package io.casehub.neocortex.corpus.management.parsers;

import com.vladsch.flexmark.html2md.converter.FlexmarkHtmlConverter;
import com.vladsch.flexmark.util.data.MutableDataSet;
import net.dankito.readability4j.Article;
import net.dankito.readability4j.Readability4J;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class HtmlDocumentParser implements DocumentParser {

    private static final Pattern TABLE_ROW = Pattern.compile("^\\|.+\\|\\s*$");
    private static final Pattern SEPARATOR_ROW = Pattern.compile("^\\|[\\s:|-]+\\|\\s*$");
    private static final Pattern IMAGE_LINE = Pattern.compile("^!\\[([^\\]]*)\\]\\(([^)]+)\\)\\s*$");

    private final FlexmarkHtmlConverter converter;

    public HtmlDocumentParser() {
        this.converter = FlexmarkHtmlConverter.builder(new MutableDataSet()).build();
    }

    @Override
    public Set<String> supportedMediaTypes() {
        return Set.of("text/html", "application/xhtml+xml");
    }

    @Override
    public ParseResult parse(InputStream content, String sourceRef, DocumentParserConfig config) {
        try {
            String html = new String(content.readAllBytes(), StandardCharsets.UTF_8);
            String url = sourceRef != null ? sourceRef : "";

            Readability4J readability = new Readability4J(url, html);
            Article article = readability.parse();

            String articleHtml = article.getContent();
            if (articleHtml == null || articleHtml.isBlank()) {
                articleHtml = html;
            }
            articleHtml = cleanBoilerplate(articleHtml);

            String markdown = convertSetextToAtx(converter.convert(articleHtml));
            String positionalMarkdown = addAnnotations(markdown);

            Map<String, String> metadata = buildMetadata(article, html);
            Layout layout = new Layout("single", LayoutPresets.resolve("single"), 1, new int[]{612, 792});

            return new ParseResult(positionalMarkdown, layout, metadata, List.of());

        } catch (IOException e) {
            throw new RuntimeException("Failed to parse HTML: " + sourceRef, e);
        }
    }

    private String convertSetextToAtx(String markdown) {
        markdown = markdown.replaceAll("(?m)^(.+)\\n={3,}\\s*$", "# $1");
        markdown = markdown.replaceAll("(?m)^(.+)\\n-{3,}\\s*$", "## $1");
        return markdown;
    }

    private String cleanBoilerplate(String html) {
        Document doc = Jsoup.parse(html);
        doc.select("script, style, noscript, svg, iframe, nav, footer, aside, header, form").remove();
        return doc.body() != null ? doc.body().html() : "";
    }

    private String addAnnotations(String markdown) {
        var sb = new StringBuilder();
        sb.append("#@ page: 1\n");
        sb.append("#@ panel: main\n");

        String[] lines = markdown.split("\n", -1);
        int i = 0;
        while (i < lines.length) {
            String line = lines[i];

            if (TABLE_ROW.matcher(line).matches()) {
                int tableStart = i;
                int cols = countColumns(line);
                while (i < lines.length && (TABLE_ROW.matcher(lines[i]).matches()
                    || SEPARATOR_ROW.matcher(lines[i]).matches())) {
                    i++;
                }
                int dataRows = 0;
                for (int j = tableStart; j < i; j++) {
                    if (!SEPARATOR_ROW.matcher(lines[j]).matches()) dataRows++;
                }
                sb.append("#@ table: rows=").append(dataRows).append(" cols=").append(cols).append('\n');
                for (int j = tableStart; j < i; j++) {
                    sb.append(lines[j]).append('\n');
                }
                continue;
            }

            Matcher imgMatcher = IMAGE_LINE.matcher(line);
            if (imgMatcher.matches()) {
                String alt = imgMatcher.group(1);
                sb.append("#@ figure: ");
                if (!alt.isBlank()) {
                    sb.append("caption=\"").append(alt.replace("\"", "\\\"")).append("\"");
                }
                sb.append('\n');
                if (!alt.isBlank()) {
                    sb.append("[").append(alt).append("]\n");
                }
                i++;
                continue;
            }

            sb.append(line).append('\n');
            i++;
        }

        return sb.toString();
    }

    private int countColumns(String row) {
        int count = 0;
        boolean started = false;
        for (char c : row.toCharArray()) {
            if (c == '|') {
                if (started) count++;
                started = true;
            }
        }
        return Math.max(count, 1);
    }

    private Map<String, String> buildMetadata(Article article, String originalHtml) {
        Map<String, String> metadata = new LinkedHashMap<>();
        metadata.put("source_format", "html");

        String title = article.getTitle();
        if (title == null || title.isBlank()) {
            Document doc = Jsoup.parse(originalHtml);
            title = doc.title();
        }
        if (title != null && !title.isBlank()) {
            metadata.put("title", title);
        }

        String author = article.getByline();
        if (author == null || author.isBlank()) {
            Document doc = Jsoup.parse(originalHtml);
            var meta = doc.selectFirst("meta[name=author]");
            if (meta != null) {
                author = meta.attr("content");
            }
        }
        if (author != null && !author.isBlank()) {
            metadata.put("author", author);
        }

        String excerpt = article.getExcerpt();
        if (excerpt != null && !excerpt.isBlank()) {
            metadata.put("description", excerpt);
        }

        return metadata;
    }
}

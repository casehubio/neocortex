package io.casehub.neocortex.corpus.management.parsers;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class HtmlDocumentParserTest {

    private final HtmlDocumentParser parser = new HtmlDocumentParser();

    @Test
    void supportedMediaTypes() {
        assertThat(parser.supportedMediaTypes()).containsExactlyInAnyOrder("text/html", "application/xhtml+xml");
    }

    @Test
    void parseSimpleArticle() {
        String html = """
            <html>
            <head><title>Test Article</title></head>
            <body>
                <article>
                    <h1>Main Heading</h1>
                    <p>First paragraph of the article.</p>
                    <p>Second paragraph with more content.</p>
                </article>
            </body>
            </html>
            """;

        var result = parse(html);

        assertThat(result.positionalMarkdown()).contains("# Main Heading");
        assertThat(result.positionalMarkdown()).contains("First paragraph of the article.");
        assertThat(result.positionalMarkdown()).contains("Second paragraph with more content.");
        assertThat(result.positionalMarkdown()).contains("#@ page: 1");
        assertThat(result.positionalMarkdown()).contains("#@ panel: main");
        assertThat(result.layout().name()).isEqualTo("single");
        assertThat(result.extractedMetadata()).containsEntry("source_format", "html");
        assertThat(result.extractedMetadata()).containsEntry("title", "Test Article");
    }

    @Test
    void parseExtractsHeadingHierarchy() {
        String html = """
            <html><body>
                <article>
                    <h1>Title</h1>
                    <h2>Section One</h2>
                    <p>Content under section one.</p>
                    <h3>Subsection</h3>
                    <p>Deeper content.</p>
                    <h2>Section Two</h2>
                    <p>Another section.</p>
                </article>
            </body></html>
            """;

        var result = parse(html);

        assertThat(result.positionalMarkdown()).contains("# Title");
        assertThat(result.positionalMarkdown()).contains("## Section One");
        assertThat(result.positionalMarkdown()).contains("### Subsection");
        assertThat(result.positionalMarkdown()).contains("## Section Two");
    }

    @Test
    void parseHandlesUnorderedList() {
        String html = """
            <html><body>
                <article>
                    <ul>
                        <li>First item</li>
                        <li>Second item</li>
                        <li>Third item</li>
                    </ul>
                </article>
            </body></html>
            """;

        var result = parse(html);

        assertThat(result.positionalMarkdown()).contains("First item");
        assertThat(result.positionalMarkdown()).contains("Second item");
        assertThat(result.positionalMarkdown()).contains("Third item");
    }

    @Test
    void parseHandlesOrderedList() {
        String html = """
            <html><body>
                <article>
                    <ol>
                        <li>Step one</li>
                        <li>Step two</li>
                    </ol>
                </article>
            </body></html>
            """;

        var result = parse(html);

        assertThat(result.positionalMarkdown()).contains("Step one");
        assertThat(result.positionalMarkdown()).contains("Step two");
    }

    @Test
    void parseHandlesTable() {
        String html = """
            <html><body>
                <article>
                    <table>
                        <thead><tr><th>Name</th><th>Value</th></tr></thead>
                        <tbody>
                            <tr><td>Alpha</td><td>100</td></tr>
                            <tr><td>Beta</td><td>200</td></tr>
                        </tbody>
                    </table>
                </article>
            </body></html>
            """;

        var result = parse(html);

        assertThat(result.positionalMarkdown()).contains("#@ table:");
        assertThat(result.positionalMarkdown()).containsPattern("\\| Name\\s+\\| Value\\s+\\|");
        assertThat(result.positionalMarkdown()).containsPattern("\\| Alpha\\s+\\| 100\\s+\\|");
    }

    @Test
    void parseHandlesBlockquote() {
        String html = """
            <html><body>
                <article>
                    <blockquote>This is a quoted passage.</blockquote>
                </article>
            </body></html>
            """;

        var result = parse(html);

        assertThat(result.positionalMarkdown()).contains("> This is a quoted passage.");
    }

    @Test
    void parseHandlesCodeBlock() {
        String html = """
            <html><body>
                <article>
                    <pre><code>int x = 42;
            return x;</code></pre>
                </article>
            </body></html>
            """;

        var result = parse(html);

        assertThat(result.positionalMarkdown()).contains("int x = 42;");
        assertThat(result.positionalMarkdown()).contains("return x;");
    }

    @Test
    void parseHandlesImages() {
        String html = """
            <html><body>
                <article>
                    <img src="photo.jpg" alt="A scenic view">
                </article>
            </body></html>
            """;

        var result = parse(html);

        assertThat(result.positionalMarkdown()).contains("A scenic view");
    }

    @Test
    void parseStripsNavigationAndBoilerplate() {
        String html = """
            <html><body>
                <nav><a href="/">Home</a><a href="/about">About</a></nav>
                <header><div class="logo">Site Logo</div></header>
                <article>
                    <h1>The Real Content</h1>
                    <p>This is what matters.</p>
                </article>
                <aside><h2>Related</h2><p>Sidebar stuff.</p></aside>
                <footer><p>Copyright 2025</p></footer>
            </body></html>
            """;

        var result = parse(html);

        assertThat(result.positionalMarkdown()).contains("The Real Content");
        assertThat(result.positionalMarkdown()).contains("This is what matters.");
        assertThat(result.positionalMarkdown()).doesNotContain("Home");
        assertThat(result.positionalMarkdown()).doesNotContain("Site Logo");
        assertThat(result.positionalMarkdown()).doesNotContain("Sidebar stuff");
        assertThat(result.positionalMarkdown()).doesNotContain("Copyright");
    }

    @Test
    void parseUsesMainElementWhenNoArticle() {
        String html = """
            <html><body>
                <nav>Navigation links</nav>
                <main>
                    <h1>Main Content</h1>
                    <p>Body text here.</p>
                </main>
                <footer>Footer text</footer>
            </body></html>
            """;

        var result = parse(html);

        assertThat(result.positionalMarkdown()).contains("Main Content");
        assertThat(result.positionalMarkdown()).contains("Body text here.");
        assertThat(result.positionalMarkdown()).doesNotContain("Navigation links");
        assertThat(result.positionalMarkdown()).doesNotContain("Footer text");
    }

    @Test
    void parseFallsBackToBodyWhenNoArticleOrMain() {
        String html = """
            <html><body>
                <h1>A Simple Page</h1>
                <p>Just some content without article tags.</p>
            </body></html>
            """;

        var result = parse(html);

        assertThat(result.positionalMarkdown()).contains("A Simple Page");
        assertThat(result.positionalMarkdown()).contains("Just some content without article tags.");
    }

    @Test
    void parseExtractsMetadataFromMetaTags() {
        String html = """
            <html>
            <head>
                <title>Page Title</title>
                <meta name="author" content="Jane Doe">
                <meta property="og:title" content="OG Title">
                <meta name="description" content="A page description.">
            </head>
            <body><p>Content.</p></body>
            </html>
            """;

        var result = parse(html);

        assertThat(result.extractedMetadata()).containsEntry("title", "Page Title");
        assertThat(result.extractedMetadata()).containsEntry("author", "Jane Doe");
    }

    @Test
    void parseRemovesScriptAndStyleContent() {
        String html = """
            <html><body>
                <script>var x = 1; alert("hi");</script>
                <style>.foo { color: red; }</style>
                <p>Visible content.</p>
            </body></html>
            """;

        var result = parse(html);

        assertThat(result.positionalMarkdown()).contains("Visible content.");
        assertThat(result.positionalMarkdown()).doesNotContain("alert");
        assertThat(result.positionalMarkdown()).doesNotContain("color: red");
    }

    @Test
    void parseHandlesInlineFormatting() {
        String html = """
            <html><body>
                <article>
                    <p>Text with <strong>bold</strong> and <em>italic</em> and <code>inline code</code>.</p>
                </article>
            </body></html>
            """;

        var result = parse(html);

        assertThat(result.positionalMarkdown()).contains("**bold**");
        assertThat(result.positionalMarkdown()).contains("*italic*");
        assertThat(result.positionalMarkdown()).contains("`inline code`");
    }

    @Test
    void parseHandlesNestedLists() {
        String html = """
            <html><body>
                <article>
                    <ul>
                        <li>Top level
                            <ul>
                                <li>Nested item</li>
                            </ul>
                        </li>
                        <li>Another top level</li>
                    </ul>
                </article>
            </body></html>
            """;

        var result = parse(html);

        assertThat(result.positionalMarkdown()).contains("Top level");
        assertThat(result.positionalMarkdown()).contains("Nested item");
        assertThat(result.positionalMarkdown()).contains("Another top level");
    }

    @Test
    void parseHandlesLinks() {
        String html = """
            <html><body>
                <article>
                    <p>Visit <a href="https://example.com">example site</a> for more.</p>
                </article>
            </body></html>
            """;

        var result = parse(html);

        assertThat(result.positionalMarkdown()).contains("[example site](https://example.com)");
    }

    @Test
    void parseHandlesEmptyHtml() {
        String html = "<html><body></body></html>";

        var result = parse(html);

        assertThat(result.positionalMarkdown()).contains("#@ page: 1");
        assertThat(result.layout().name()).isEqualTo("single");
    }

    @Test
    void parseHandlesTableWithoutThead() {
        String html = """
            <html><body>
                <article>
                    <table>
                        <tr><td>A</td><td>B</td></tr>
                        <tr><td>C</td><td>D</td></tr>
                    </table>
                </article>
            </body></html>
            """;

        var result = parse(html);

        assertThat(result.positionalMarkdown()).containsPattern("\\| A\\s+\\| B\\s+\\|");
    }

    @Test
    void parseContentDensityFallback() {
        String html = """
            <html><body>
                <div class="sidebar"><p>Short.</p></div>
                <div class="content">
                    <h2>Article Title</h2>
                    <p>This is a much longer paragraph with substantial content that should
                    score higher in content density analysis than the short sidebar text.
                    It contains multiple sentences and meaningful information that a reader
                    would want to consume.</p>
                    <p>Another paragraph of real article content that adds to the density
                    score of this container element.</p>
                </div>
                <div class="ads"><p>Ad text.</p></div>
            </body></html>
            """;

        var result = parse(html);

        assertThat(result.positionalMarkdown()).contains("Article Title");
        assertThat(result.positionalMarkdown()).contains("substantial content");
    }

    @Test
    void parseHandlesHr() {
        String html = """
            <html><body>
                <article>
                    <p>Before.</p>
                    <hr>
                    <p>After.</p>
                </article>
            </body></html>
            """;

        var result = parse(html);

        assertThat(result.positionalMarkdown()).contains("Before.");
        assertThat(result.positionalMarkdown()).contains("After.");
    }

    private ParseResult parse(String html) {
        return parser.parse(
            new ByteArrayInputStream(html.getBytes(StandardCharsets.UTF_8)),
            "test.html",
            DocumentParserConfig.defaults()
        );
    }
}

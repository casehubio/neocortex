package io.casehub.neocortex.corpus.management.parsers;

import java.io.InputStream;
import java.util.Set;

public interface DocumentParser {
    ParseResult parse(InputStream content, String sourceRef, DocumentParserConfig config);
    Set<String> supportedMediaTypes();
}

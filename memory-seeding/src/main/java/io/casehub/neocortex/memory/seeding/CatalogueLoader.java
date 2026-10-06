package io.casehub.neocortex.memory.seeding;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.casehub.yaml.jackson.YamlMappers;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

public class CatalogueLoader {

    private final ObjectMapper mapper = YamlMappers.create();

    public CatalogueIndex loadIndex(Path catalogueDir) {
        Path indexFile = catalogueDir.resolve("index.yaml");
        try {
            return mapper.readValue(indexFile.toFile(), CatalogueIndex.class);
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to load catalogue index from " + indexFile, e);
        }
    }

    public List<CatalogueEntry> loadAll(Path catalogueDir) {
        List<CatalogueEntry> all = new ArrayList<>();
        try (Stream<Path> files = Files.list(catalogueDir)) {
            files.filter(p -> p.toString().endsWith(".yaml"))
                 .filter(p -> !p.getFileName().toString().equals("index.yaml"))
                 .sorted()
                 .forEach(p -> {
                     try {
                         var wrapper = mapper.readValue(p.toFile(), CatalogueFileWrapper.class);
                         if (wrapper.entries() != null) all.addAll(wrapper.entries());
                     } catch (IOException e) {
                         throw new UncheckedIOException("Failed to load catalogue file " + p, e);
                     }
                 });
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to list catalogue directory " + catalogueDir, e);
        }
        validate(all);
        return List.copyOf(all);
    }

    public Optional<CatalogueEntry> findEntry(List<CatalogueEntry> entries, String entryId) {
        return entries.stream().filter(e -> e.id().equals(entryId)).findFirst();
    }

    private void validate(List<CatalogueEntry> entries) {
        var ids = new HashSet<String>();
        for (var entry : entries) {
            if (!ids.add(entry.id())) {
                throw new IllegalStateException("Duplicate catalogue entry ID: " + entry.id());
            }
            for (var trigger : entry.triggers()) {
                if (trigger.intensity() == null || trigger.intensity().size() != 2)
                    throw new IllegalStateException("Trigger intensity must be [min, max] for entry " + entry.id());
                for (double v : trigger.intensity()) {
                    if (v < 0.0 || v > 1.0)
                        throw new IllegalStateException("Trigger intensity out of [0,1] for entry " + entry.id());
                }
            }
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record CatalogueFileWrapper(List<CatalogueEntry> entries) {}
}

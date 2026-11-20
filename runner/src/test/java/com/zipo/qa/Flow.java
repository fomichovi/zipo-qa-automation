package com.zipo.qa;

import java.io.IOException;
import java.io.Reader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;
import org.yaml.snakeyaml.Yaml;

/** A Maestro test flow file plus the name and tags from its header. */
public record Flow(Path file, String name, List<String> tags) {

    /** Safe folder name for this flow's reports, e.g. "tc02_subscribe". */
    public String id() {
        return file.getFileName().toString().replaceFirst("\\.ya?ml$", "");
    }

    public boolean matches(Set<String> wantedTags) {
        return wantedTags.isEmpty() || tags.stream().anyMatch(wantedTags::contains);
    }

    /**
     * Finds test flows using the "flows:" patterns in flows/config.yaml (e.g. "paywall/*"),
     * so the Maestro config stays the single source of truth for what counts as a test.
     */
    public static List<Flow> discover(Path flowsDir) {
        List<Flow> flows = new ArrayList<>();
        for (String pattern : configPatterns(flowsDir)) {
            Path dir = flowsDir.resolve(pattern.replaceFirst("/?\\*$", ""));
            try (Stream<Path> files = Files.list(dir)) {
                files.filter(f -> f.toString().matches(".*\\.ya?ml$"))
                        .sorted(Comparator.comparing(Path::toString))
                        .map(Flow::load)
                        .forEach(flows::add);
            } catch (IOException e) {
                throw new IllegalStateException("Cannot list flows in " + dir, e);
            }
        }
        return flows;
    }

    @SuppressWarnings("unchecked")
    private static List<String> configPatterns(Path flowsDir) {
        Path config = flowsDir.resolve("config.yaml");
        try (Reader reader = Files.newBufferedReader(config)) {
            Map<String, Object> yaml = new Yaml().load(reader);
            Object patterns = yaml == null ? null : yaml.get("flows");
            if (!(patterns instanceof List<?> list) || list.isEmpty()) {
                throw new IllegalStateException(config + " has no 'flows:' list");
            }
            return (List<String>) list;
        } catch (IOException e) {
            throw new IllegalStateException("Cannot read " + config, e);
        }
    }

    @SuppressWarnings("unchecked")
    private static Flow load(Path file) {
        try (Reader reader = Files.newBufferedReader(file)) {
            // The header is the first YAML document (before "---").
            Map<String, Object> header = (Map<String, Object>) new Yaml().loadAll(reader).iterator().next();
            String name = String.valueOf(header.getOrDefault("name", file.getFileName().toString()));
            List<String> tags = (List<String>) header.getOrDefault("tags", List.of());
            return new Flow(file, name, List.copyOf(tags));
        } catch (IOException | RuntimeException e) {
            throw new IllegalStateException("Cannot read flow header of " + file, e);
        }
    }
}

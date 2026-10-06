package org.codingmatters.poom.ci.apps.releaser.graph;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;

import java.io.File;
import java.io.IOException;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;

public class TagVersions {
    static private final Pattern VERSION = Pattern.compile("^\\d+\\.\\d+\\.\\d+(\\.\\d+)?$");

    static public TagVersions from(File file) throws IOException {
        if(file == null || file.length() == 0L) {
            return new TagVersions(Collections.emptyMap());
        }

        Map<String, String> read = new ObjectMapper(new YAMLFactory())
                .readValue(file, new TypeReference<Map<String, String>>() {});
        if(read == null) {
            return new TagVersions(Collections.emptyMap());
        }

        Map<String, String> versions = new LinkedHashMap<>();
        for (Map.Entry<String, String> entry : read.entrySet()) {
            String version = entry.getValue();
            if(version == null || ! VERSION.matcher(version).matches()) {
                throw new IllegalArgumentException(String.format(
                        "%s : %s is not a version, expected <major>.<minor>.<patch> or <major>.<minor>.<patch>.<support>",
                        entry.getKey(), version));
            }
            versions.put(entry.getKey(), version);
        }

        return new TagVersions(versions);
    }

    private final Map<String, String> versions;

    private TagVersions(Map<String, String> versions) {
        this.versions = versions;
    }

    public boolean isEmpty() {
        return this.versions.isEmpty();
    }

    public Optional<String> tagFor(String repository) {
        return Optional.ofNullable(this.versions.get(repository));
    }

    public Set<String> repositories() {
        return Collections.unmodifiableSet(this.versions.keySet());
    }
}

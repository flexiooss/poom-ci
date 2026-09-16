package org.codingmatters.poom.ci.apps.releaser.graph;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import org.codingmatters.poom.ci.apps.releaser.maven.pom.ArtifactCoordinates;

import java.io.File;
import java.io.IOException;
import java.util.List;

public class PropagatedVersions {

    static public PropagationContext from(File file) throws IOException {
        PropagationContext result = new PropagationContext();
        if(file == null || file.length() == 0L) {
            return result;
        }

        List<String> coordinates = new ObjectMapper(new YAMLFactory())
                .readValue(file, new TypeReference<List<String>>() {});
        if(coordinates == null) {
            return result;
        }

        for (String coordinate : coordinates) {
            result.addPropagatedArtifact(ArtifactCoordinates.from(coordinate));
        }

        return result;
    }
}

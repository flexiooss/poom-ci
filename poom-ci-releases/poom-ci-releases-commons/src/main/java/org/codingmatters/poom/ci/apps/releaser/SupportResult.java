package org.codingmatters.poom.ci.apps.releaser;

import org.codingmatters.poom.ci.apps.releaser.maven.pom.ArtifactCoordinates;

public class SupportResult {
    private final ArtifactCoordinates coordinates;
    private final String branch;

    public SupportResult(ArtifactCoordinates coordinates, String branch) {
        this.coordinates = coordinates;
        this.branch = branch;
    }

    public ArtifactCoordinates coordinates() {
        return this.coordinates;
    }

    public String branch() {
        return this.branch;
    }
}

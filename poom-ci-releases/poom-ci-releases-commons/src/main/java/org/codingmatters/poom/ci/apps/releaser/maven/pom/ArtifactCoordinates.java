package org.codingmatters.poom.ci.apps.releaser.maven.pom;

import java.util.Objects;

public class ArtifactCoordinates {
    private String groupId;
    private String artifactId;
    private String version;

    public ArtifactCoordinates() {
    }

    public ArtifactCoordinates(String groupId, String artifactId, String version) {
        this.groupId = groupId;
        this.artifactId = artifactId;
        this.version = version;
    }

    public String getArtifactId() {
        return artifactId;
    }

    public void setArtifactId(String artifactId) {
        this.artifactId = artifactId;
    }

    public String getVersion() {
        return version;
    }

    public void setVersion(String version) {
        this.version = version;
    }

    public String getGroupId() {
        return groupId;
    }

    public void setGroupId(String groupId) {
        this.groupId = groupId;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ArtifactCoordinates that = (ArtifactCoordinates) o;
        return Objects.equals(groupId, that.groupId) &&
                Objects.equals(artifactId, that.artifactId) &&
                Objects.equals(version, that.version);
    }

    @Override
    public int hashCode() {
        return Objects.hash(groupId, artifactId, version);
    }

    @Override
    public String toString() {
        return "ArtifactCoordinatesDesc{" +
                "groupId='" + groupId + '\'' +
                ", artifactId='" + artifactId + '\'' +
                ", version='" + version + '\'' +
                '}';
    }

    static public ArtifactCoordinates from(String coordinates) {
        if(coordinates == null) {
            throw new IllegalArgumentException("not a maven coordinate : null");
        }
        String[] parts = coordinates.split(":", -1);
        if(parts.length != 3) {
            throw new IllegalArgumentException("not a maven coordinate, expected groupId:artifactId:version : " + coordinates);
        }
        for (String part : parts) {
            if(part.trim().isEmpty()) {
                throw new IllegalArgumentException("not a maven coordinate, empty segment : " + coordinates);
            }
        }
        return new ArtifactCoordinates(parts[0].trim(), parts[1].trim(), parts[2].trim());
    }

    public String coodinates() {
        return String.format("%s:%s:%s", this.getGroupId(), this.getArtifactId(), this.getVersion());
    }

    public boolean matches(ArtifactCoordinates a) {
        if(this.getGroupId() == null) {
            if(a.getGroupId() != null) {
                return false;
            }
        } else {
            if(! this.getGroupId().equals(a.getGroupId())) {
                return false;
            }
        }

        if(this.getArtifactId() == null) {
            return a.getArtifactId() == null;
        } else {
            return this.getArtifactId().equals(a.getArtifactId());
        }
    }
}

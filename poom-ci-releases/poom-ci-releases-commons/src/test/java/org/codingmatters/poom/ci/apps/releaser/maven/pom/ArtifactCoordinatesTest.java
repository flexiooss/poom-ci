package org.codingmatters.poom.ci.apps.releaser.maven.pom;

import org.junit.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;
import static org.junit.Assert.fail;

public class ArtifactCoordinatesTest {

    @Test
    public void givenMatching__whenGroupAndArtifactSetted_andGroupAndArtifactMatch_andVersionDiffers__thenMatches() throws Exception {
        assertThat(
                new ArtifactCoordinates("a", "b", "1").matches(new ArtifactCoordinates("a", "b", "2")),
                is(true)
        );
    }

    @Test
    public void givenMatching__whenGroupAndArtifactSetted_andGroupAndArtifactMatch_andVersionMatches__thenMatches() throws Exception {
        assertThat(
                new ArtifactCoordinates("a", "b", "1").matches(new ArtifactCoordinates("a", "b", "1")),
                is(true)
        );
    }

    @Test
    public void givenMatching__whenGroupAndArtifactSetted_andGroupAndVersionMatch_andArtifactDiffers__thenDoesntMatch() throws Exception {
        assertThat(
                new ArtifactCoordinates("a", "b", "1").matches(new ArtifactCoordinates("a", "c", "1")),
                is(false)
        );
    }

    @Test
    public void givenMatching__whenGroupAndArtifactSetted_andArtifactAndVersionMatch_andGroupDiffers__thenDoesntMatch() throws Exception {
        assertThat(
                new ArtifactCoordinates("a", "b", "1").matches(new ArtifactCoordinates("c", "b", "1")),
                is(false)
        );
    }

    @Test
    public void givenMatching__whenGroupIsNull_andArtifactAndVersionMatch_andGroupMatches__thenMatches() throws Exception {
        assertThat(
                new ArtifactCoordinates(null, "b", "1").matches(new ArtifactCoordinates(null, "b", "1")),
                is(true)
        );
    }

    @Test
    public void givenMatching__whenGroupIsNull_andArtifactAndVersionMatch_andGroupDiffers__thenDoesntMatch() throws Exception {
        assertThat(
                new ArtifactCoordinates(null, "b", "1").matches(new ArtifactCoordinates("a", "b", "1")),
                is(false)
        );
    }

    @Test
    public void givenMatching__whenArtifactIsNull_andGroupAndVersionMatch_andArtifactDiffers__thenDoesntMatch() throws Exception {
        assertThat(
                new ArtifactCoordinates("a", null, "1").matches(new ArtifactCoordinates("a", "b", "1")),
                is(false)
        );
    }

    @Test
    public void givenMatching__whenArtifactIsNull_andGroupAndVersionMatch_andArtifactMatch__thenMatch() throws Exception {
        assertThat(
                new ArtifactCoordinates("a", null, "1").matches(new ArtifactCoordinates("a", null, "1")),
                is(true)
        );
    }

    @Test
    public void givenFrom__whenThreeSegments__thenCoordinatesParsed() throws Exception {
        ArtifactCoordinates actual = ArtifactCoordinates.from("io.flexio:flexio-service-parent:1.617.0");

        assertThat(actual.getGroupId(), is("io.flexio"));
        assertThat(actual.getArtifactId(), is("flexio-service-parent"));
        assertThat(actual.getVersion(), is("1.617.0"));
    }

    @Test
    public void givenFrom__whenThreeSegments__thenRoundTripsWithCoodinates() throws Exception {
        assertThat(
                ArtifactCoordinates.from("io.flexio:flexio-service-parent:1.617.0").coodinates(),
                is("io.flexio:flexio-service-parent:1.617.0")
        );
    }

    @Test
    public void givenFrom__whenTwoSegments__thenIllegalArgument() throws Exception {
        try {
            ArtifactCoordinates.from("io.flexio:flexio-service-parent");
            fail("expected IllegalArgumentException");
        } catch (IllegalArgumentException e) {
            assertThat(e.getMessage(), containsString("io.flexio:flexio-service-parent"));
        }
    }

    @Test
    public void givenFrom__whenFourSegments__thenIllegalArgument() throws Exception {
        try {
            ArtifactCoordinates.from("io.flexio:a:1.0.0:extra");
            fail("expected IllegalArgumentException");
        } catch (IllegalArgumentException e) {
            assertThat(e.getMessage(), containsString("io.flexio:a:1.0.0:extra"));
        }
    }

    @Test
    public void givenFrom__whenEmptySegment__thenIllegalArgument() throws Exception {
        try {
            ArtifactCoordinates.from("io.flexio::1.0.0");
            fail("expected IllegalArgumentException");
        } catch (IllegalArgumentException e) {
            assertThat(e.getMessage(), containsString("io.flexio::1.0.0"));
        }
    }

    @Test
    public void givenFrom__whenNull__thenIllegalArgument() throws Exception {
        try {
            ArtifactCoordinates.from(null);
            fail("expected IllegalArgumentException");
        } catch (IllegalArgumentException e) {
        }
    }
}

package org.codingmatters.poom.ci.apps.releaser.task;

import org.codingmatters.poom.ci.apps.releaser.graph.PropagationContext;
import org.codingmatters.poom.ci.apps.releaser.maven.pom.ArtifactCoordinates;
import org.junit.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;

public class AbstractGraphTaskTest {

    @Test
    public void givenEmptyContext__whenFormattingPropagatedVersions__thenNothingIsAdded() throws Exception {
        assertThat(AbstractGraphTask.formattedPropagatedVersions(new PropagationContext()), is(""));
    }

    @Test
    public void givenNoContext__whenFormattingPropagatedVersions__thenNothingIsAdded() throws Exception {
        assertThat(AbstractGraphTask.formattedPropagatedVersions(null), is(""));
    }

    @Test
    public void givenContextWithTwoArtifacts__whenFormattingPropagatedVersions__thenBothAreListedUnderAHeader() throws Exception {
        PropagationContext context = new PropagationContext();
        context.addPropagatedArtifact(ArtifactCoordinates.from("io.flexio:flexio-service-parent:1.617.0"));
        context.addPropagatedArtifact(ArtifactCoordinates.from("io.flexio.apis:flexio-apis:2.34.1"));

        String actual = AbstractGraphTask.formattedPropagatedVersions(context);

        assertThat(actual, containsString("Propagated versions :"));
        assertThat(actual, containsString("io.flexio:flexio-service-parent:1.617.0"));
        assertThat(actual, containsString("io.flexio.apis:flexio-apis:2.34.1"));
    }

    @Test
    public void givenContextWithOneArtifact__whenFormattingPropagatedVersions__thenStartsWithABlankLine() throws Exception {
        PropagationContext context = new PropagationContext();
        context.addPropagatedArtifact(ArtifactCoordinates.from("io.flexio:flexio-service-parent:1.617.0"));

        assertThat(AbstractGraphTask.formattedPropagatedVersions(context), startsWith("\n\n"));
    }
}

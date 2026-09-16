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
    public void givenContextWithOneArtifact__whenFormattingPropagatedVersions__thenStartsWithTheHeader() throws Exception {
        assertThat(AbstractGraphTask.formattedPropagatedVersions(this.oneArtifact()), startsWith("Propagated versions :"));
    }

    @Test
    public void givenContextWithOneArtifact__whenFormattingPropagatedVersions__thenEndsWithABlankLine() throws Exception {
        assertThat(AbstractGraphTask.formattedPropagatedVersions(this.oneArtifact()), endsWith("\n\n"));
    }

    @Test
    public void givenStartMessage__whenContextIsNotEmpty__thenPropagatedVersionsComeBeforeRepositories() throws Exception {
        String actual = AbstractGraphTask.formattedStartMessage("Repositories :\n   - Flexio-corp/a-project", this.oneArtifact());

        assertThat(actual.indexOf("Propagated versions :"), is(0));
        assertThat(actual.indexOf("Propagated versions :"), lessThan(actual.indexOf("Repositories :")));
    }

    @Test
    public void givenStartMessage__whenContextIsEmpty__thenOnlyRepositories() throws Exception {
        assertThat(
                AbstractGraphTask.formattedStartMessage("Repositories :\n   - Flexio-corp/a-project", new PropagationContext()),
                is("Repositories :\n   - Flexio-corp/a-project")
        );
    }

    private PropagationContext oneArtifact() {
        PropagationContext context = new PropagationContext();
        context.addPropagatedArtifact(ArtifactCoordinates.from("io.flexio:flexio-service-parent:1.617.0"));
        return context;
    }
}

package org.codingmatters.poom.ci.apps.releaser.graph;

import org.codingmatters.poom.ci.apps.releaser.graph.descriptors.RepositoryGraphDescriptor;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.io.FileWriter;
import java.io.Writer;
import java.util.Collections;
import java.util.List;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.junit.Assert.fail;

public class SupportLinePlacementTest {

    @Rule
    public TemporaryFolder dir = new TemporaryFolder();

    private List<RepositoryGraphDescriptor> graph(String resource) throws Exception {
        return Collections.singletonList(RepositoryGraphDescriptor.fromYaml(
                Thread.currentThread().getContextClassLoader().getResourceAsStream(resource)));
    }

    private TagVersions tagVersions(String content) throws Exception {
        File file = this.dir.newFile();
        try (Writer writer = new FileWriter(file)) {
            writer.write(content);
        }
        return TagVersions.from(file);
    }

    @Test
    public void givenEmptyTagVersions__whenChecking__thenNothingRaised() throws Exception {
        SupportLinePlacement.check(TagVersions.from(null), this.graph("graphs/support-with-successors.yml"));
    }

    @Test
    public void givenRepositoryLastOfItsFanBranch__whenChecking__thenNothingRaised() throws Exception {
        SupportLinePlacement.check(
                this.tagVersions("---\norg/leaf-b: \"1.29.0\"\n"),
                this.graph("graphs/support-terminal.yml"));
    }

    @Test
    public void givenRepositoryFollowedInItsList__whenChecking__thenFailsNamingBoth() throws Exception {
        try {
            SupportLinePlacement.check(
                    this.tagVersions("---\norg/low: \"1.29.0\"\n"),
                    this.graph("graphs/support-with-successors.yml"));
            fail("expected IllegalArgumentException");
        } catch (IllegalArgumentException e) {
            assertThat(e.getMessage(), containsString("org/low"));
            assertThat(e.getMessage(), containsString("org/middle"));
        }
    }

    @Test
    public void givenRepositoryWithAThenBelow__whenChecking__thenFailsNamingTheSuccessor() throws Exception {
        try {
            SupportLinePlacement.check(
                    this.tagVersions("---\norg/leaf-a: \"1.29.0\"\n"),
                    this.graph("graphs/support-in-fan.yml"));
            fail("expected IllegalArgumentException");
        } catch (IllegalArgumentException e) {
            assertThat(e.getMessage(), containsString("org/leaf-a"));
            assertThat(e.getMessage(), containsString("org/leaf-b"));
        }
    }

    @Test
    public void givenRepositoryAbsentFromEveryGraph__whenChecking__thenNothingRaised() throws Exception {
        SupportLinePlacement.check(
                this.tagVersions("---\norg/typo: \"1.29.0\"\n"),
                this.graph("graphs/support-terminal.yml"));
    }

    @Test
    public void givenGraphFilteredByFrom__whenAListedRepositoryFellOut__thenNothingRaised() throws Exception {
        List<RepositoryGraphDescriptor> filtered = Collections.singletonList(
                RepositoryGraphDescriptor.fromYaml(
                        Thread.currentThread().getContextClassLoader()
                                .getResourceAsStream("graphs/support-with-successors.yml"))
                        .subgraph("org/middle"));

        SupportLinePlacement.check(this.tagVersions("---\norg/low: \"1.29.0\"\n"), filtered);
    }
}

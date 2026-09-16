package org.codingmatters.poom.ci.apps.releaser;

import org.codingmatters.poom.ci.apps.releaser.command.RecordingCommandHelper;
import org.codingmatters.poom.ci.apps.releaser.graph.PropagationContext;
import org.codingmatters.poom.ci.apps.releaser.maven.pom.ArtifactCoordinates;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.util.List;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;

public class HotfixTest {

    static private final String POM =
            "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" +
            "<project xmlns=\"http://maven.apache.org/POM/4.0.0\"\n" +
            "         xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\"\n" +
            "         xsi:schemaLocation=\"http://maven.apache.org/POM/4.0.0 http://maven.apache.org/xsd/maven-4.0.0.xsd\">\n" +
            "    <modelVersion>4.0.0</modelVersion>\n" +
            "\n" +
            "    <groupId>io.flexio</groupId>\n" +
            "    <artifactId>a-project</artifactId>\n" +
            "    <version>1.49.0</version>\n" +
            "\n" +
            "</project>\n";

    @Rule
    public TemporaryFolder dir = new TemporaryFolder();

    private RecordingCommandHelper helper;

    @Before
    public void setUp() throws Exception {
        this.helper = new RecordingCommandHelper();
        this.helper.onCloneWritePom(POM);
        this.helper.stdout("1.49.1");
    }

    private ArtifactCoordinates hotfix() throws Exception {
        return new Hotfix(
                "git@github.com:Flexio-corp/a-project.git",
                new PropagationContext(),
                this.helper,
                new Workspace(this.dir.getRoot())
        ).initiate();
    }

    @Test
    public void givenRepository__whenHotfixing__thenMasterNeverMerged() throws Exception {
        this.hotfix();

        assertThat(this.helper.commands(), not(hasItem(startsWith("git merge"))));
    }

    @Test
    public void givenRepository__whenHotfixing__thenBothBranchesCheckedOut() throws Exception {
        this.hotfix();

        assertThat(this.helper.commands(), hasItem("git checkout master"));
        assertThat(this.helper.commands(), hasItem("git checkout develop"));
    }

    @Test
    public void givenRepository__whenHotfixing__thenFlowStartedAndFinished() throws Exception {
        this.hotfix();

        assertThat(this.helper.commands(), hasItem("flexio-flow hotfix start -D"));
        assertThat(this.helper.commands(), hasItem("flexio-flow hotfix finish -D"));
    }

    @Test
    public void givenRepository__whenHotfixing__thenVersionIsReadFromMasterAfterFinish() throws Exception {
        this.hotfix();

        List<String> commands = this.helper.commands();
        int finish = commands.indexOf("flexio-flow hotfix finish -D");
        int checkout = commands.lastIndexOf("git checkout master");
        int version = commands.lastIndexOf("flexio-flow version");

        assertThat("hotfix finish must have run", finish, greaterThan(-1));
        assertThat("master must be checked out after finish", checkout, greaterThan(finish));
        assertThat("version must be read after being back on master", version, greaterThan(checkout));
    }

    @Test
    public void givenRepository__whenHotfixing__thenResultCarriesVersionFromFlow() throws Exception {
        ArtifactCoordinates actual = this.hotfix();

        assertThat(actual.coodinates(), is("io.flexio:a-project:1.49.1"));
    }
}

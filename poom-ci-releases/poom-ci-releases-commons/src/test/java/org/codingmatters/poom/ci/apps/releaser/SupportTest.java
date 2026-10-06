package org.codingmatters.poom.ci.apps.releaser;

import org.codingmatters.poom.ci.apps.releaser.command.RecordingCommandHelper;
import org.codingmatters.poom.ci.apps.releaser.graph.PropagationContext;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.util.List;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;

public class SupportTest {

    static private final String POM =
            "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" +
            "<project xmlns=\"http://maven.apache.org/POM/4.0.0\"\n" +
            "         xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\"\n" +
            "         xsi:schemaLocation=\"http://maven.apache.org/POM/4.0.0 http://maven.apache.org/xsd/maven-4.0.0.xsd\">\n" +
            "    <modelVersion>4.0.0</modelVersion>\n" +
            "\n" +
            "    <groupId>io.flexio</groupId>\n" +
            "    <artifactId>a-project</artifactId>\n" +
            "    <version>1.29.0</version>\n" +
            "\n" +
            "</project>\n";

    @Rule
    public TemporaryFolder dir = new TemporaryFolder();

    private RecordingCommandHelper helper;

    @Before
    public void setUp() throws Exception {
        this.helper = new RecordingCommandHelper();
        this.helper.onCloneWritePom(POM);
        this.helper.stdout("1.29.0.1", "* support/1.29.0.1-dev#56");
    }

    private SupportResult support() throws Exception {
        return new Support(
                "git@github.com:Flexio-corp/a-project.git",
                "1.29.0",
                new PropagationContext(),
                this.helper,
                new Workspace(this.dir.getRoot())
        ).initiate();
    }

    @Test
    public void givenRepository__whenSupporting__thenMasterNeverCheckedOutNorMerged() throws Exception {
        this.support();

        assertThat(this.helper.commands(), not(hasItem("git checkout master")));
        assertThat(this.helper.commands(), not(hasItem(startsWith("git merge"))));
    }

    @Test
    public void givenRepository__whenSupporting__thenDevelopCheckedOutForTheReport() throws Exception {
        this.support();

        assertThat(this.helper.commands(), hasItem("git checkout develop"));
    }

    @Test
    public void givenRepository__whenSupporting__thenFlowStartedFromTagAndFinishedWithMerge() throws Exception {
        this.support();

        assertThat(this.helper.commands(), hasItem("flexio-flow support-branch start --from-tag=1.29.0 -D"));
        assertThat(this.helper.commands(), hasItem("flexio-flow support-branch finish --merge -D"));
    }

    @Test
    public void givenRepository__whenSupporting__thenVersionIsReadBetweenStartAndFinish() throws Exception {
        this.support();

        List<String> commands = this.helper.commands();
        int start = commands.indexOf("flexio-flow support-branch start --from-tag=1.29.0 -D");
        int version = commands.indexOf("flexio-flow version");
        int finish = commands.indexOf("flexio-flow support-branch finish --merge -D");

        assertThat("start must have run", start, greaterThan(-1));
        assertThat("version must be read after start", version, greaterThan(start));
        assertThat("version must be read before finish, the branch is gone after", version, lessThan(finish));
    }

    @Test
    public void givenBranchNameCarryingAnIssueRef__whenSupporting__thenBranchIsReadFromGitNotComputed() throws Exception {
        SupportResult actual = this.support();

        assertThat(this.helper.commands(), hasItem("git branch"));
        assertThat(actual.branch(), is("support/1.29.0.1-dev#56"));
    }

    @Test
    public void givenRepository__whenSupporting__thenResultCarriesVersionFromFlow() throws Exception {
        SupportResult actual = this.support();

        assertThat(actual.coordinates().coodinates(), is("io.flexio:a-project:1.29.0.1"));
    }
}

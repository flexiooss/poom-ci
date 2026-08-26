package org.codingmatters.poom.ci.apps.releaser.git;

import org.codingmatters.poom.ci.apps.releaser.command.CommandHelper;
import org.codingmatters.poom.ci.apps.releaser.command.exception.CommandFailed;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.util.concurrent.atomic.AtomicInteger;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.io.FileMatchers.anExistingFile;
import static org.junit.Assert.fail;

/**
 * Cloning is done over ssh in production and a transient ssh failure used to abort a whole release graph.
 * These tests use a local repository as remote so that they stay offline.
 */
public class GitCloneRetryTest {

    @Rule
    public TemporaryFolder dir = new TemporaryFolder();

    private final AtomicInteger fatalCount = new AtomicInteger(0);
    private final CommandHelper commandHelper = new CommandHelper(
            line -> {},
            line -> { if (line.contains("fatal")) this.fatalCount.incrementAndGet(); }
    );

    private File remote;

    @Before
    public void setUp() throws Exception {
        this.remote = this.dir.newFolder("remote");
        this.run(this.remote, "git", "init", "-q", ".");
        this.run(this.remote, "sh", "-c", "echo hello > README.md");
        this.run(this.remote, "git", "add", ".");
        this.run(this.remote, "git", "-c", "user.email=t@t", "-c", "user.name=t", "commit", "-qm", "init");
    }

    @Test
    public void givenDestinationHoldsAPartialCloneFromAFailedAttempt__whenCloning__thenItIsCleanedAndCloneSucceeds() throws Exception {
        File workspace = this.dir.newFolder("workspace");
        assertThat(new File(workspace, ".git").mkdir(), is(true));
        assertThat(new File(workspace, ".git/config").createNewFile(), is(true));
        assertThat(new File(workspace, "halfway.txt").createNewFile(), is(true));

        new Git(workspace, this.commandHelper).clone(this.remote.getAbsolutePath());

        assertThat(new File(workspace, "README.md"), is(anExistingFile()));
        assertThat(new File(workspace, "halfway.txt").exists(), is(false));
    }

    @Test
    public void givenDestinationHoldsForeignContent__whenCloning__thenCloneFailsWithoutDeletingAnything() throws Exception {
        File workspace = this.dir.newFolder("workspace");
        File precious = new File(workspace, "precious.txt");
        assertThat(precious.createNewFile(), is(true));

        try {
            new Git(workspace, this.commandHelper).clone(this.remote.getAbsolutePath());
            fail("expected a CommandFailed rather than wiping a directory that is not a clone");
        } catch (CommandFailed e) {
            assertThat(e.getMessage(), containsString("is not a git clone"));
        }

        assertThat(precious, is(anExistingFile()));
    }

    @Test
    public void whenCloneKeepsFailing__thenEveryAttemptIsMadeBeforeGivingUp() throws Exception {
        File workspace = this.dir.newFolder("workspace");

        try {
            new Git(workspace, this.commandHelper, 3, 1L).clone(new File(this.dir.getRoot(), "no-such-repo").getAbsolutePath());
            fail("expected a CommandFailed when the repository cannot be cloned");
        } catch (CommandFailed e) {
            assertThat(e.getMessage(), is("git clone " + new File(this.dir.getRoot(), "no-such-repo").getAbsolutePath() + " failed 3 times in a row, giving up"));
        }

        // git reports at least one fatal line per attempt
        assertThat(this.fatalCount.get(), is(greaterThanOrEqualTo(3)));
    }

    private void run(File workdir, String... command) throws Exception {
        Process process = new ProcessBuilder(command).directory(workdir).inheritIO().start();
        assertThat(process.waitFor(), is(0));
    }
}

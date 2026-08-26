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
import static org.junit.Assert.fail;

/**
 * Pushing goes over ssh in production and is exposed to the same transient failures as cloning.
 * These tests use a local bare repository as remote so that they stay offline.
 */
public class GitPushRetryTest {

    @Rule
    public TemporaryFolder dir = new TemporaryFolder();

    private final AtomicInteger fatalCount = new AtomicInteger(0);
    private final CommandHelper commandHelper = new CommandHelper(
            line -> {},
            line -> { if (line.contains("fatal")) this.fatalCount.incrementAndGet(); }
    );

    private File bareRemote;
    private File workspace;

    @Before
    public void setUp() throws Exception {
        this.bareRemote = this.dir.newFolder("remote.git");
        this.run(this.bareRemote, "git", "init", "-q", "--bare", ".");

        this.workspace = this.dir.newFolder("workspace");
        new Git(this.workspace, this.commandHelper).clone(this.bareRemote.getAbsolutePath());
        this.run(this.workspace, "git", "config", "user.email", "t@t");
        this.run(this.workspace, "git", "config", "user.name", "t");
        this.run(this.workspace, "sh", "-c", "echo hello > README.md");
        this.run(this.workspace, "git", "add", ".");
        this.run(this.workspace, "git", "commit", "-qm", "a commit to push");
    }

    @Test
    public void whenPushSucceeds__thenCommitReachesTheRemote() throws Exception {
        new GitRepository(this.commandHelper, this.workspace).push();

        assertThat(this.stdout(this.bareRemote, "git", "log", "--oneline"), containsString("a commit to push"));
        assertThat(this.fatalCount.get(), is(0));
    }

    @Test
    public void whenPushKeepsFailing__thenEveryAttemptIsMadeBeforeGivingUp() throws Exception {
        this.run(this.workspace, "git", "remote", "set-url", "origin", new File(this.dir.getRoot(), "gone.git").getAbsolutePath());

        try {
            new GitRepository(this.commandHelper, this.workspace, 3, 1L).push();
            fail("expected a CommandFailed when the remote cannot be reached");
        } catch (CommandFailed e) {
            assertThat(e.getMessage(), is("git push failed 3 times in a row, giving up"));
        }

        // git reports at least one fatal line per attempt (it actually reports two for a push)
        assertThat(this.fatalCount.get(), is(greaterThanOrEqualTo(3)));
    }

    private void run(File workdir, String... command) throws Exception {
        Process process = new ProcessBuilder(command).directory(workdir).inheritIO().start();
        assertThat(process.waitFor(), is(0));
    }

    private String stdout(File workdir, String... command) throws Exception {
        StringBuilder result = new StringBuilder();
        new CommandHelper(line -> result.append(line).append("\n"), line -> {})
                .exec(new ProcessBuilder(command).directory(workdir), String.join(" ", command));
        return result.toString();
    }
}

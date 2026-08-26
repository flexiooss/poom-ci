package org.codingmatters.poom.ci.apps.releaser.command;

import org.codingmatters.poom.ci.apps.releaser.command.exception.CommandFailed;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.is;
import static org.junit.Assert.fail;

public class CommandHelperTest {

    @Rule
    public TemporaryFolder dir = new TemporaryFolder();

    private final CommandHelper commandHelper = new CommandHelper(line -> {}, line -> {});

    @Test
    public void whenCommandSucceedsAtFirstTry__thenCommandIsRunOnlyOnce() throws Exception {
        File workdir = this.dir.newFolder();

        this.commandHelper.execWithRetry(this.succeedingAtAttempt(workdir, 1), "flaky command", 5, 1L);

        assertThat(this.attemptCount(workdir), is(1));
    }

    @Test
    public void whenCommandFailsThenSucceeds__thenCommandIsRetriedUntilSuccess() throws Exception {
        File workdir = this.dir.newFolder();

        this.commandHelper.execWithRetry(this.succeedingAtAttempt(workdir, 3), "flaky command", 5, 1L);

        assertThat(this.attemptCount(workdir), is(3));
    }

    @Test
    public void whenCommandKeepsFailing__thenAllAttemptsAreMadeAndCommandFailed() throws Exception {
        File workdir = this.dir.newFolder();

        try {
            this.commandHelper.execWithRetry(this.succeedingAtAttempt(workdir, 99), "hopeless command", 3, 1L);
            fail("expected a CommandFailed when every attempt fails");
        } catch (CommandFailed e) {
            assertThat(e.getMessage(), containsString("hopeless command"));
        }

        assertThat(this.attemptCount(workdir), is(3));
    }

    /**
     * A command counting its own invocations in a file, failing until the given attempt is reached.
     */
    private ProcessBuilder succeedingAtAttempt(File workdir, int attempt) {
        return new ProcessBuilder("sh", "-c", String.format(
                "count=$(cat count 2>/dev/null || echo 0) ; count=$((count + 1)) ; echo $count > count ; [ $count -ge %s ]",
                attempt
        )).directory(workdir);
    }

    private int attemptCount(File workdir) throws IOException {
        File count = new File(workdir, "count");
        if (!count.exists()) return 0;
        return Integer.parseInt(new String(Files.readAllBytes(count.toPath()), StandardCharsets.UTF_8).trim());
    }
}

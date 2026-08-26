package org.codingmatters.poom.ci.apps.releaser.git;

import org.codingmatters.poom.ci.apps.releaser.command.CommandHelper;
import org.codingmatters.poom.ci.apps.releaser.command.exception.CommandFailed;

import java.io.File;

public class GitRepository {
    private final CommandHelper commandHelper;
    private final File repository;
    private final int pushAttempts;
    private final long delayBetweenPushAttempts;

    public GitRepository(CommandHelper commandHelper, File repository) {
        this(commandHelper, repository, Git.DEFAULT_CLONE_ATTEMPTS, Git.DEFAULT_DELAY_BETWEEN_CLONE_ATTEMPTS);
    }

    public GitRepository(CommandHelper commandHelper, File repository, int pushAttempts, long delayBetweenPushAttempts) {
        this.commandHelper = commandHelper;
        this.repository = repository;
        this.pushAttempts = pushAttempts;
        this.delayBetweenPushAttempts = delayBetweenPushAttempts;
    }

    public void checkout(String branch) throws CommandFailed {
        ProcessBuilder processBuilder = new ProcessBuilder()
                .directory(this.repository)
                .command("git", "checkout", branch)
                ;
        this.commandHelper.exec(processBuilder, "git checkout " + branch);
    }

    public void merge(String withBranch, String message) throws CommandFailed {
        ProcessBuilder processBuilder = new ProcessBuilder()
                .directory(this.repository)
                .command("git", "merge", withBranch, "-m", message)
                ;
        this.commandHelper.exec(processBuilder, "git merge " + withBranch);
    }

    public void commit(String message) throws CommandFailed {
        ProcessBuilder processBuilder = new ProcessBuilder()
                .directory(this.repository)
                .command("git", "commit", "-am", message)
                ;
        this.commandHelper.exec(processBuilder, "git commit -am \"" + message + "\"");
    }

    public void emptyCommit(String message) throws CommandFailed {
        ProcessBuilder processBuilder = new ProcessBuilder()
                .directory(this.repository)
                .command("git", "commit", "--allow-empty", "-m", message)
                ;
        this.commandHelper.exec(processBuilder, "git commit --allow-empty -m \"" + message + "\"");
    }

    public void push() throws CommandFailed {
        ProcessBuilder processBuilder = new ProcessBuilder()
                .directory(this.repository)
                .command("git", "push")
                ;
        this.commandHelper.execWithRetry(processBuilder, "git push", this.pushAttempts, this.delayBetweenPushAttempts);
    }
}

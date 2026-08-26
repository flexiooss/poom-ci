package org.codingmatters.poom.ci.apps.releaser.git;

import org.codingmatters.poom.ci.apps.releaser.command.CommandHelper;
import org.codingmatters.poom.ci.apps.releaser.command.exception.CommandFailed;

import java.io.File;
import java.nio.file.Files;
import java.util.concurrent.atomic.AtomicReference;

public class Git {
    /**
     * Cloning goes over ssh and is subject to transient infrastructure failures (ssh agent hiccup, network
     * glitch, github throttling). As a single failed clone used to abort a whole release graph, clones are
     * retried before giving up.
     */
    static public final int DEFAULT_CLONE_ATTEMPTS = 3;
    static public final long DEFAULT_DELAY_BETWEEN_CLONE_ATTEMPTS = 5000L;

    private final File workspace;
    private final CommandHelper commandHelper;
    private final int cloneAttempts;
    private final long delayBetweenCloneAttempts;

    public Git(File workspace, CommandHelper commandHelper) {
        this(workspace, commandHelper, DEFAULT_CLONE_ATTEMPTS, DEFAULT_DELAY_BETWEEN_CLONE_ATTEMPTS);
    }

    public Git(File workspace, CommandHelper commandHelper, int cloneAttempts, long delayBetweenCloneAttempts) {
        this.workspace = workspace;
        this.commandHelper = commandHelper;
        this.cloneAttempts = cloneAttempts;
        this.delayBetweenCloneAttempts = delayBetweenCloneAttempts;
    }

    public GitRepository clone(String repositoryUrl) throws CommandFailed {
        ProcessBuilder processBuilder = new ProcessBuilder()
                .directory(workspace)
                .command("git", "clone", repositoryUrl, workspace.getAbsolutePath())
                ;
        this.commandHelper.execWithRetry(
                processBuilder,
                "git clone " + repositoryUrl,
                this.cloneAttempts,
                this.delayBetweenCloneAttempts,
                attempt -> this.emptyWorkspace()
        );
        return new GitRepository(this.commandHelper, this.workspace);
    }

    /**
     * git clone refuses a non empty destination, so whatever a previous attempt left behind must go. Only a
     * directory holding a clone is ever emptied : anything else is somebody else's data and cloning fails
     * rather than deleting it.
     */
    private void emptyWorkspace() throws CommandFailed {
        File[] children = this.workspace.exists() ? this.workspace.listFiles() : new File[0];
        if(children == null) {
            throw new CommandFailed("cannot list clone destination " + this.workspace.getAbsolutePath());
        }
        if(children.length > 0 && ! new File(this.workspace, ".git").exists()) {
            throw new CommandFailed(String.format(
                    "refusing to clone into %s : it is not empty and is not a git clone, will not delete its content",
                    this.workspace.getAbsolutePath()
            ));
        }
        for (File child : children) {
            this.recursiveDelete(child);
        }
        if(! this.workspace.exists() && ! this.workspace.mkdirs()) {
            throw new CommandFailed("failed creating clone destination " + this.workspace.getAbsolutePath());
        }
    }

    private void recursiveDelete(File file) throws CommandFailed {
        if(file.isDirectory() && ! Files.isSymbolicLink(file.toPath())) {
            File[] children = file.listFiles();
            if(children != null) {
                for (File child : children) {
                    this.recursiveDelete(child);
                }
            }
        }
        if(! file.delete()) {
            throw new CommandFailed("failed cleaning clone destination, could not delete " + file.getAbsolutePath());
        }
    }

    public String branch() throws CommandFailed {
        String[] lines = this.commandHelper.execWithStdout(new ProcessBuilder("git", "branch").directory(workspace), "git branch");
        for (String line : lines) {
            if(line.startsWith("*")) {
                return line.replace("*", "").trim();
            }
        }
        return null;
    }

    public String remoteOrigin() throws CommandFailed {
        String[] lines = this.commandHelper.execWithStdout(
                new ProcessBuilder("git", "config", "--get", "remote.origin.url").directory(workspace),
                "git config --get remote.origin.url"
        );
        return lines.length> 0 ? lines[0] : null;
    }

    public String username() throws CommandFailed {
        String[] lines = this.commandHelper.execWithStdout(
                new ProcessBuilder("git", "config", "user.name").directory(workspace),
                "git config user.name"
        );
        return lines.length> 0 ? lines[0] : null;
    }

    public String email() throws CommandFailed {
        String[] lines = this.commandHelper.execWithStdout(
                new ProcessBuilder("git", "config", "user.email").directory(workspace),
                "git config user.name"
        );
        return lines.length> 0 ? lines[0] : null;
    }


    public String checkoutSpec() throws CommandFailed {
        return String.format("git|%s|%s", this.remoteOrigin(), this.branch());
    }
}

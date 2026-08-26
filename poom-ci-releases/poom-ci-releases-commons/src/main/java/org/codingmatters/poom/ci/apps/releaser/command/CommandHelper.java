package org.codingmatters.poom.ci.apps.releaser.command;

import org.codingmatters.poom.ci.apps.releaser.command.exception.CommandFailed;
import org.codingmatters.poom.services.support.process.ProcessInvoker;

import java.io.IOException;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;

public class CommandHelper {
    private final ProcessInvoker.OutputListener outputListener;
    private final ProcessInvoker.ErrorListener errorListener;

    public CommandHelper(ProcessInvoker.OutputListener outputListener, ProcessInvoker.ErrorListener errorListener) {
        this.outputListener = outputListener;
        this.errorListener = errorListener;
    }

    public void exec(ProcessBuilder processBuilder, String commandName) throws CommandFailed {
        executeWith(processBuilder, commandName, this.outputListener, this.errorListener);
    }

    public String[] execWithStdout(ProcessBuilder processBuilder, String commandName) throws CommandFailed {
        List<String> results = Collections.synchronizedList(new LinkedList<>());
        executeWith(processBuilder, commandName, line -> results.add(line), this.errorListener);
        return results.toArray(new String[0]);
    }

    public void execWithRetry(ProcessBuilder processBuilder, String commandName, int maxAttempts, long delayBetweenAttempts) throws CommandFailed {
        this.execWithRetry(processBuilder, commandName, maxAttempts, delayBetweenAttempts, attempt -> {});
    }

    /**
     * Runs the command, retrying it as long as it exits with a non zero status. The beforeAttempt hook is
     * run before each attempt, giving the caller a chance to reset the state a failed attempt may have left
     * behind (a partially cloned directory for instance).
     */
    public void execWithRetry(ProcessBuilder processBuilder, String commandName, int maxAttempts, long delayBetweenAttempts, BeforeAttempt beforeAttempt) throws CommandFailed {
        CommandFailed lastFailure = null;
        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            beforeAttempt.run(attempt);
            try {
                this.exec(processBuilder, commandName);
                return;
            } catch (CommandFailed e) {
                lastFailure = e;
                if (attempt < maxAttempts) {
                    System.err.printf("%s failed (attempt %s/%s), retrying in %sms : %s\n",
                            commandName, attempt, maxAttempts, delayBetweenAttempts, e.getMessage());
                    try {
                        Thread.sleep(delayBetweenAttempts);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        throw new CommandFailed(commandName + " interrupted while waiting for retry", ie);
                    }
                }
            }
        }
        throw new CommandFailed(String.format("%s failed %s times in a row, giving up", commandName, maxAttempts), lastFailure);
    }

    static private void executeWith(ProcessBuilder processBuilder, String commandName, ProcessInvoker.OutputListener out, ProcessInvoker.ErrorListener err) throws CommandFailed {
        try {
            ProcessInvoker invoker = new ProcessInvoker();
            int status = invoker.exec(processBuilder, out, err);
            if (status != 0) {
                throw new CommandFailed(commandName + "failed with status " + status);
            }
        } catch (IOException e) {
            throw new CommandFailed(commandName + "failed", e);
        } catch (InterruptedException e) {
            throw new CommandFailed(commandName + "failed", e);
        }
    }

    @FunctionalInterface
    public interface BeforeAttempt {
        void run(int attempt) throws CommandFailed;
    }
}

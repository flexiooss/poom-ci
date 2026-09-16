package org.codingmatters.poom.ci.apps.releaser.command;

import org.codingmatters.poom.ci.apps.releaser.command.exception.CommandFailed;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.Writer;
import java.util.LinkedList;
import java.util.List;

public class RecordingCommandHelper extends CommandHelper {

    private final List<String> commands = new LinkedList<>();
    private String[] stdout = new String[] {""};
    private String pomContent = null;

    public RecordingCommandHelper() {
        super(line -> {}, line -> {});
    }

    public List<String> commands() {
        return this.commands;
    }

    public void stdout(String... lines) {
        this.stdout = lines;
    }

    public void onCloneWritePom(String pomContent) {
        this.pomContent = pomContent;
    }

    @Override
    public void exec(ProcessBuilder processBuilder, String commandName) throws CommandFailed {
        this.record(processBuilder);
    }

    @Override
    public String[] execWithStdout(ProcessBuilder processBuilder, String commandName) throws CommandFailed {
        this.record(processBuilder);
        return this.stdout;
    }

    @Override
    public void execWithRetry(ProcessBuilder processBuilder, String commandName, int maxAttempts, long delayBetweenAttempts) throws CommandFailed {
        this.record(processBuilder);
    }

    @Override
    public void execWithRetry(ProcessBuilder processBuilder, String commandName, int maxAttempts, long delayBetweenAttempts, BeforeAttempt beforeAttempt) throws CommandFailed {
        this.record(processBuilder);
    }

    private void record(ProcessBuilder processBuilder) throws CommandFailed {
        String command = String.join(" ", processBuilder.command());
        this.commands.add(command);
        if(this.pomContent != null && command.startsWith("git clone")) {
            File directory = processBuilder.directory();
            directory.mkdirs();
            try (Writer writer = new FileWriter(new File(directory, "pom.xml"))) {
                writer.write(this.pomContent);
            } catch (IOException e) {
                throw new CommandFailed("failed writing test pom", e);
            }
        }
    }
}

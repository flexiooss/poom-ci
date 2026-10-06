package org.codingmatters.poom.ci.apps.releaser.task;

import org.codingmatters.poom.ci.apps.releaser.RepositoryPipeline;
import org.codingmatters.poom.ci.apps.releaser.Support;
import org.codingmatters.poom.ci.apps.releaser.SupportResult;
import org.codingmatters.poom.ci.apps.releaser.Workspace;
import org.codingmatters.poom.ci.apps.releaser.command.CommandHelper;
import org.codingmatters.poom.ci.apps.releaser.git.GithubRepositoryUrlProvider;
import org.codingmatters.poom.ci.apps.releaser.graph.PropagationContext;
import org.codingmatters.poom.ci.pipeline.api.types.Pipeline;
import org.codingmatters.poom.ci.pipeline.api.types.pipeline.Status;
import org.codingmatters.poom.ci.pipeline.client.PoomCIPipelineAPIClient;
import org.codingmatters.poom.services.support.date.UTC;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.concurrent.Callable;

public class SupportTask implements Callable<ReleaseTaskResult> {
    private final String repository;
    private final String repositoryUrl;
    private final String tag;
    private final PropagationContext propagationContext;
    private final CommandHelper commandHelper;
    private final PoomCIPipelineAPIClient client;
    private final Workspace workspace;

    public SupportTask(String repository, String tag, GithubRepositoryUrlProvider githubRepositoryUrlProvider, PropagationContext propagationContext, CommandHelper commandHelper, PoomCIPipelineAPIClient client, Workspace workspace) {
        this.repository = repository;
        this.repositoryUrl = githubRepositoryUrlProvider.url(repository);
        this.tag = tag;
        this.propagationContext = propagationContext;
        this.commandHelper = commandHelper;
        this.client = client;
        this.workspace = workspace;
    }

    @Override
    public ReleaseTaskResult call() throws Exception {
        LocalDateTime start = UTC.now();

        SupportResult supported = new Support(this.repositoryUrl, this.tag, this.propagationContext, this.commandHelper, this.workspace).initiate();

        RepositoryPipeline pipeline = new RepositoryPipeline(this.repository, supported.branch(), this.client);
        Optional<Pipeline> pipe = pipeline.last(start);
        if (!pipe.isPresent()) {
            System.out.printf("Waiting for support pipeline to start on %s...\n", supported.branch());
            do {
                Thread.sleep(2000L);
                pipe = pipeline.last(start);
            } while (!pipe.isPresent());
        }

        System.out.println("waiting for support pipeline to finish...");
        Pipeline done = pipeline.awaitDone(pipe.get());

        if (done.status().exit().equals(Status.Exit.SUCCESS)) {
            return new ReleaseTaskResult(ReleaseTaskResult.ExitStatus.SUCCESS, String.format("%s supported from tag %s to version %s", this.repository, this.tag, supported.coordinates()), supported.coordinates());
        } else {
            System.err.println("support failed !!");
            return new ReleaseTaskResult(ReleaseTaskResult.ExitStatus.FAILURE, String.format("%s support failed", this.repository), null);
        }
    }
}

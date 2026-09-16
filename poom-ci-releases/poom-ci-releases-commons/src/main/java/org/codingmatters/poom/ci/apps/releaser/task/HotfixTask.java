package org.codingmatters.poom.ci.apps.releaser.task;

import org.codingmatters.poom.ci.apps.releaser.Hotfix;
import org.codingmatters.poom.ci.apps.releaser.RepositoryPipeline;
import org.codingmatters.poom.ci.apps.releaser.Workspace;
import org.codingmatters.poom.ci.apps.releaser.command.CommandHelper;
import org.codingmatters.poom.ci.apps.releaser.git.GithubRepositoryUrlProvider;
import org.codingmatters.poom.ci.apps.releaser.graph.PropagationContext;
import org.codingmatters.poom.ci.apps.releaser.maven.pom.ArtifactCoordinates;
import org.codingmatters.poom.ci.pipeline.api.types.Pipeline;
import org.codingmatters.poom.ci.pipeline.api.types.pipeline.Status;
import org.codingmatters.poom.ci.pipeline.client.PoomCIPipelineAPIClient;
import org.codingmatters.poom.services.support.date.UTC;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.concurrent.Callable;

public class HotfixTask implements Callable<ReleaseTaskResult> {
    private final String repository;
    private final String repositoryUrl;
    private final PropagationContext propagationContext;
    private final CommandHelper commandHelper;
    private final PoomCIPipelineAPIClient client;
    private final Workspace workspace;

    public HotfixTask(String repository, GithubRepositoryUrlProvider githubRepositoryUrlProvider, PropagationContext propagationContext, CommandHelper commandHelper, PoomCIPipelineAPIClient client, Workspace workspace) {
        this.repository = repository;
        this.repositoryUrl = githubRepositoryUrlProvider.url(repository);
        this.propagationContext = propagationContext;
        this.commandHelper = commandHelper;
        this.client = client;
        this.workspace = workspace;
    }

    @Override
    public ReleaseTaskResult call() throws Exception {
        LocalDateTime start = UTC.now();

        ArtifactCoordinates hotfixedCoordinates = new Hotfix(this.repositoryUrl, this.propagationContext, this.commandHelper, this.workspace).initiate();

        RepositoryPipeline pipeline = new RepositoryPipeline(this.repository, "master", this.client);
        Optional<Pipeline> pipe = pipeline.last(start);
        if (!pipe.isPresent()) {
            System.out.println("Waiting for hotfix pipeline to start...");
            do {
                Thread.sleep(2000L);
                pipe = pipeline.last(start);
            } while (!pipe.isPresent());
        }

        System.out.println("waiting for hotfix pipeline to finish...");
        Pipeline done = pipeline.awaitDone(pipe.get());

        if (done.status().exit().equals(Status.Exit.SUCCESS)) {
            return new ReleaseTaskResult(ReleaseTaskResult.ExitStatus.SUCCESS, String.format("%s hotfixed to version %s", this.repository, hotfixedCoordinates), hotfixedCoordinates);
        } else {
            System.err.println("hotfix failed !!");
            System.exit(1);
            return new ReleaseTaskResult(ReleaseTaskResult.ExitStatus.FAILURE, String.format("%s hotfix failed", this.repository), null);
        }
    }
}

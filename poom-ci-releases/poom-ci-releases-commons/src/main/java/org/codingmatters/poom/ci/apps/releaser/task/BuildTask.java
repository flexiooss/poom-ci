package org.codingmatters.poom.ci.apps.releaser.task;

import org.codingmatters.poom.ci.apps.releaser.ProjectDescriptor;
import org.codingmatters.poom.ci.apps.releaser.RepositoryPipeline;
import org.codingmatters.poom.ci.apps.releaser.Workspace;
import org.codingmatters.poom.ci.apps.releaser.command.CommandHelper;
import org.codingmatters.poom.ci.apps.releaser.command.exception.CommandFailed;
import org.codingmatters.poom.ci.apps.releaser.flow.FlexioFlow;
import org.codingmatters.poom.ci.apps.releaser.git.Git;
import org.codingmatters.poom.ci.apps.releaser.git.GitRepository;
import org.codingmatters.poom.ci.apps.releaser.git.GithubRepositoryUrlProvider;
import org.codingmatters.poom.ci.apps.releaser.graph.PropagationContext;
import org.codingmatters.poom.ci.apps.releaser.hb.JsPackage;
import org.codingmatters.poom.ci.apps.releaser.maven.Pom;
import org.codingmatters.poom.ci.pipeline.api.types.Pipeline;
import org.codingmatters.poom.ci.pipeline.api.types.pipeline.Status;
import org.codingmatters.poom.ci.pipeline.client.PoomCIPipelineAPIClient;
import org.codingmatters.poom.services.logging.CategorizedLogger;
import org.codingmatters.poom.services.support.date.UTC;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.Callable;

public class BuildTask implements Callable<ReleaseTaskResult> {
    static private CategorizedLogger log = CategorizedLogger.getLogger(BuildTask.class);

    private final String repository;
    private final String repositoryUrl;
    private final String branch;
    private final PropagationContext propagationContext;
    private final CommandHelper commandHelper;
    private final PoomCIPipelineAPIClient client;
    private final Workspace workspace;

    public BuildTask(String repository, GithubRepositoryUrlProvider githubRepositoryUrlProvider, String branch, PropagationContext propagationContext, CommandHelper commandHelper, PoomCIPipelineAPIClient client, Workspace workspace) {
        this.repository = repository;
        this.repositoryUrl = githubRepositoryUrlProvider.url(repository);
        this.branch = branch;
        this.propagationContext = propagationContext;
        this.commandHelper = commandHelper;
        this.client = client;
        this.workspace = workspace;
    }

    @Override
    public ReleaseTaskResult call() throws Exception {
        LocalDateTime start = UTC.now();
        try {
            System.out.println("####################################################################################");
            System.out.printf("Building %s/%s with context :\n", this.repository, this.branch);
            System.out.println(this.propagationContext.text());
            System.out.println("####################################################################################");

            File repoDir = this.workspace.mkdir(UUID.randomUUID().toString());
            repoDir.mkdir();

            GitRepository repository = new Git(repoDir, this.commandHelper).clone(this.repositoryUrl);
            repository.checkout(this.branch);
            repository.emptyCommit("build trigged from releaser");
            repository.push();

            this.waitForBuild(start);

            ProjectDescriptor currentPom = this.readProjectDescriptor(repoDir);
            this.propagationContext.addPropagatedArtifact(currentPom.project());

            return new ReleaseTaskResult(ReleaseTaskResult.ExitStatus.SUCCESS, "built " + this.repository + "/" + this.branch, currentPom.project());
        } catch (CommandFailed e) {

            return new ReleaseTaskResult(ReleaseTaskResult.ExitStatus.FAILURE, "failure building " + this.repository + "/" + this.branch, null);
        }

    }

    private ProjectDescriptor readProjectDescriptor(File workspace) throws CommandFailed {
        if(new File(workspace, "pom.xml").exists()) {
            try (InputStream pjDescFile = new FileInputStream(new File(workspace, "pom.xml"))) {
                return Pom.from(pjDescFile);
            } catch (IOException e) {
                throw new CommandFailed("failed reading pom for " + this.repositoryUrl, e);
            }
        } else
        if(new File(workspace, "package.json").exists()) {
            try (InputStream pjDescFile = new FileInputStream(new File(workspace, "package.json"))) {
                return JsPackage.read(pjDescFile);
            } catch (IOException e) {
                throw new CommandFailed("failed reading pom for " + this.repositoryUrl, e);
            }
        } else {
            return null;
        }
    }

    private void waitForBuild(LocalDateTime start) throws IOException, InterruptedException {
        RepositoryPipeline pipeline = new RepositoryPipeline(this.repository, this.branch, this.client);
        Thread.sleep(2000L);
        Optional<Pipeline> pipe = pipeline.last(start);
        if (!pipe.isPresent()) {
            System.out.println("Waiting for build pipeline to start...");
            do {
                Thread.sleep(2000L);
                pipe = pipeline.last(start);
            } while (!pipe.isPresent());
        }

        System.out.printf("waiting for pipeline %s to finish...\n", pipe.get().opt().id().orElse("NONE"));
        while (!pipe.get().opt().status().run().orElse(Status.Run.PENDING).equals(Status.Run.DONE)) {
            Thread.sleep(2000L);
            pipe = pipeline.last(start);
        }
    }
}

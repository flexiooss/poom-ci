package org.codingmatters.poom.ci.apps.releaser.task;

import org.codingmatters.poom.ci.apps.releaser.Workspace;
import org.codingmatters.poom.ci.apps.releaser.command.CommandHelper;
import org.codingmatters.poom.ci.apps.releaser.git.GithubRepositoryUrlProvider;
import org.codingmatters.poom.ci.apps.releaser.graph.GraphWalker;
import org.codingmatters.poom.ci.apps.releaser.graph.PropagationContext;
import org.codingmatters.poom.ci.apps.releaser.graph.TagVersions;
import org.codingmatters.poom.ci.apps.releaser.graph.descriptors.RepositoryGraphDescriptor;
import org.codingmatters.poom.ci.apps.releaser.notify.Notifier;
import org.codingmatters.poom.ci.pipeline.client.PoomCIPipelineAPIClient;
import org.codingmatters.poom.services.logging.CategorizedLogger;

import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class HotfixGraphTask extends AbstractGraphTask implements Callable<GraphTaskResult> {
    static private final CategorizedLogger log = CategorizedLogger.getLogger(HotfixGraphTask.class);

    private final PropagationContext propagationContext;
    private final TagVersions tagVersions;

    public HotfixGraphTask(
            List<RepositoryGraphDescriptor> descriptorList,
            PropagationContext propagationContext,
            TagVersions tagVersions,
            CommandHelper commandHelper,
            PoomCIPipelineAPIClient client,
            Workspace workspace,
            Notifier notifier,
            GithubRepositoryUrlProvider githubRepositoryUrlProvider,
            GraphTaskListener graphTaskListener) {
        super(descriptorList, commandHelper, client, workspace, notifier, githubRepositoryUrlProvider, graphTaskListener);
        this.propagationContext = propagationContext;
        this.tagVersions = tagVersions;
    }

    @Override
    public GraphTaskResult call() throws Exception {
        ExecutorService pool = Executors.newCachedThreadPool();
        try {
            log.info("starting hotfix-graph for {}", this.descriptorList);
            notifier.notify("hotfix-graph", "START", formattedStartMessage(this.formattedRepositoryList(descriptorList), this.propagationContext));
            GraphWalker.WalkerTaskProvider walkerTaskProvider = (repository, context) ->
                    this.tagVersions.tagFor(repository)
                            .<Callable<ReleaseTaskResult>>map(tag -> new SupportTask(repository, tag, githubRepositoryUrlProvider, context, commandHelper, client, workspace))
                            .orElseGet(() -> new HotfixTask(repository, githubRepositoryUrlProvider, context, commandHelper, client, workspace));

            for (RepositoryGraphDescriptor descriptor : descriptorList) {
                walkGraph(descriptor, this.propagationContext, pool, walkerTaskProvider);
            }

            notifier.notify("hotfix-graph", "DONE", this.propagationContext.text());
            return new GraphTaskResult(ReleaseTaskResult.ExitStatus.SUCCESS, "Finished hotfixing graphs", this.propagationContext);
        } finally {
            pool.shutdownNow();
            log.info("hotfix-graph for {} done", this.descriptorList);
        }
    }
}

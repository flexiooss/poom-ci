package org.codingmatters.poom.ci.apps.releaser.task;

import org.codingmatters.poom.ci.apps.releaser.Workspace;
import org.codingmatters.poom.ci.apps.releaser.command.RecordingCommandHelper;
import org.codingmatters.poom.ci.apps.releaser.git.GithubRepositoryUrlProvider;
import org.codingmatters.poom.ci.apps.releaser.graph.PropagationContext;
import org.codingmatters.poom.ci.pipeline.api.PipelinesGetResponse;
import org.codingmatters.poom.ci.pipeline.api.PoomCIPipelineAPIHandlers;
import org.codingmatters.poom.ci.pipeline.api.types.Pipeline;
import org.codingmatters.poom.ci.pipeline.api.types.pipeline.Status;
import org.codingmatters.poom.ci.pipeline.client.PoomCIPipelineAPIClient;
import org.codingmatters.poom.ci.pipeline.client.PoomCIPipelineAPIHandlersClient;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

public class HotfixTaskTest {

    static private final String POM =
            "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" +
            "<project xmlns=\"http://maven.apache.org/POM/4.0.0\">\n" +
            "    <modelVersion>4.0.0</modelVersion>\n" +
            "    <groupId>io.flexio</groupId>\n" +
            "    <artifactId>a-project</artifactId>\n" +
            "    <version>1.49.0</version>\n" +
            "</project>\n";

    @Rule
    public TemporaryFolder dir = new TemporaryFolder();

    private ExecutorService pool;
    private RecordingCommandHelper helper;

    @Before
    public void setUp() throws Exception {
        this.pool = Executors.newFixedThreadPool(2);
        this.helper = new RecordingCommandHelper();
        this.helper.onCloneWritePom(POM);
        this.helper.stdout("1.49.1");
    }

    @After
    public void tearDown() throws Exception {
        this.pool.shutdownNow();
    }

    private PoomCIPipelineAPIClient clientWith(Status.Exit exit) {
        PoomCIPipelineAPIHandlers handlers = new PoomCIPipelineAPIHandlers.Builder()
                .pipelinesGetHandler(request -> PipelinesGetResponse.builder()
                        .status200(status -> status.payload(Pipeline.builder()
                                .id("42")
                                .status(s -> s.run(Status.Run.DONE).exit(exit))
                                .build()))
                        .build())
                .build();
        return new PoomCIPipelineAPIHandlersClient(handlers, this.pool);
    }

    private ReleaseTaskResult hotfixWith(Status.Exit exit) throws Exception {
        return new HotfixTask(
                "Flexio-corp/a-project",
                GithubRepositoryUrlProvider.ssh(),
                new PropagationContext(),
                this.helper,
                this.clientWith(exit),
                new Workspace(this.dir.getRoot())
        ).call();
    }

    @Test
    public void givenPipelineFailed__whenHotfixing__thenFailureIsReturnedAndTheVmSurvives() throws Exception {
        ReleaseTaskResult actual = this.hotfixWith(Status.Exit.FAILURE);

        assertThat(actual.exitStatus(), is(ReleaseTaskResult.ExitStatus.FAILURE));
    }

    @Test
    public void givenPipelineSucceeded__whenHotfixing__thenSuccessIsReturned() throws Exception {
        ReleaseTaskResult actual = this.hotfixWith(Status.Exit.SUCCESS);

        assertThat(actual.exitStatus(), is(ReleaseTaskResult.ExitStatus.SUCCESS));
    }
}

package org.codingmatters.poom.ci.apps.releaser;

import org.codingmatters.poom.ci.pipeline.api.PipelineGetResponse;
import org.codingmatters.poom.ci.pipeline.api.PoomCIPipelineAPIHandlers;
import org.codingmatters.poom.ci.pipeline.api.types.Pipeline;
import org.codingmatters.poom.ci.pipeline.api.types.pipeline.Status;
import org.codingmatters.poom.ci.pipeline.client.PoomCIPipelineAPIHandlersClient;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.io.IOException;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.hamcrest.Matchers.is;
import static org.junit.Assert.fail;

public class RepositoryPipelineTest {

    private final List<PipelineGetResponse> responses = new LinkedList<>();
    private final AtomicInteger getCount = new AtomicInteger(0);

    private ExecutorService pool;
    private RepositoryPipeline pipeline;

    @Before
    public void setUp() throws Exception {
        this.pool = Executors.newFixedThreadPool(2);
        PoomCIPipelineAPIHandlers handlers = new PoomCIPipelineAPIHandlers.Builder()
                .pipelineGetHandler(request -> {
                    this.getCount.incrementAndGet();
                    return this.responses.size() > 1 ? this.responses.remove(0) : this.responses.get(0);
                })
                .build();
        this.pipeline = new RepositoryPipeline("flexiooss/poom-ci", "master", new PoomCIPipelineAPIHandlersClient(handlers, this.pool));
    }

    @After
    public void tearDown() throws Exception {
        this.pool.shutdownNow();
    }

    @Test
    public void whenPipelineIsAlreadyDone__thenPipelineIsReturnedWithoutUpdating() throws Exception {
        this.responses.add(this.response(Status.Run.DONE));

        assertThat(this.pipeline.awaitDone(this.pipe(Status.Run.DONE), 1L, 3).status().run(), is(Status.Run.DONE));
        assertThat(this.getCount.get(), is(0));
    }

    @Test
    public void whenPipelineIsRunning__thenPipelineIsPolledUntilDone() throws Exception {
        this.responses.add(this.response(Status.Run.RUNNING));
        this.responses.add(this.response(Status.Run.DONE));

        assertThat(this.pipeline.awaitDone(this.pipe(Status.Run.RUNNING), 1L, 3).status().run(), is(Status.Run.DONE));
        assertThat(this.getCount.get(), is(2));
    }

    @Test
    public void whenPipelineUpdateFailsOnce__thenPollingGoesOnUntilDone() throws Exception {
        this.responses.add(this.response(Status.Run.RUNNING));
        this.responses.add(PipelineGetResponse.builder().status500(status -> {}).build());
        this.responses.add(this.response(Status.Run.DONE));

        assertThat(this.pipeline.awaitDone(this.pipe(Status.Run.RUNNING), 1L, 3).status().run(), is(Status.Run.DONE));
        assertThat(this.getCount.get(), is(3));
    }

    @Test
    public void whenPipelineUpdateKeepsFailing__thenIOException() throws Exception {
        this.responses.add(PipelineGetResponse.builder().status404(status -> {}).build());

        try {
            this.pipeline.awaitDone(this.pipe(Status.Run.RUNNING), 1L, 3);
            fail("expected an IOException when pipeline cannot be updated anymore");
        } catch (IOException e) {
            assertThat(e.getMessage(), is("pipeline 42 update failed 3 times in a row, giving up"));
        }
        assertThat(this.getCount.get(), is(greaterThanOrEqualTo(3)));
    }

    private Pipeline pipe(Status.Run run) {
        return Pipeline.builder().id("42").status(status -> status.run(run).exit(Status.Exit.SUCCESS)).build();
    }

    private PipelineGetResponse response(Status.Run run) {
        return PipelineGetResponse.builder().status200(status -> status.payload(this.pipe(run))).build();
    }
}

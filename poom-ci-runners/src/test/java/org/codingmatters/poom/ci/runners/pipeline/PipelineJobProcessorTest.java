package org.codingmatters.poom.ci.runners.pipeline;

import org.codingmatters.poom.ci.pipeline.api.*;
import org.codingmatters.poom.ci.pipeline.api.types.*;
import org.codingmatters.poom.ci.pipeline.client.PoomCIPipelineAPIClient;
import org.codingmatters.poom.ci.pipeline.client.PoomCIPipelineAPIHandlersClient;
import org.codingmatters.poom.ci.pipeline.descriptors.Pipeline;
import org.codingmatters.poom.ci.pipeline.descriptors.StageHolder;
import org.codingmatters.poom.services.support.Env;
import org.codingmatters.poom.services.support.EnvProvider;
import org.codingmatters.poom.services.tests.Eventually;
import org.codingmatters.poom.runner.exception.JobProcessingException;
import org.codingmatters.poomjobs.api.types.Job;
import org.codingmatters.poomjobs.api.types.job.Status;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.IOException;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.hamcrest.Matchers.*;
import static org.hamcrest.MatcherAssert.assertThat;

public class PipelineJobProcessorTest {

    private PoomCIPipelineAPIClient pipelineClient = new PoomCIPipelineAPIHandlersClient(
            new PoomCIPipelineAPIHandlers.Builder()
                    .pipelineGetHandler(this::pipelineGet)
                    .pipelineStagesPostHandler(this::stagePost)
                    .pipelineStagePatchHandler(this::stagePatch)
                    .pipelineStageLogsPatchHandler(this::logsPatch)
                    .pipelinePatchHandler(this::pipelinePatch)
                    .build(),
            Executors.newFixedThreadPool(4)
    );

    private final List<PipelineGetRequest> pipelineGetCalls = Collections.synchronizedList(new LinkedList<>());
    private PipelineGetResponse pipelineGet(PipelineGetRequest request) {
        pipelineGetCalls.add(request);
        return PipelineGetResponse.builder()
                .status200(status -> status
                        .xEntityId(request.pipelineId())
                        .payload(pipeline -> pipeline
                                .trigger(trigger -> trigger.triggerId("trigger-id").type(PipelineTrigger.Type.GITHUB_PUSH))
                        )
                )
                .build();
    }

    private final List<PipelinePatchRequest> pipelinePatchCalls = Collections.synchronizedList(new LinkedList<>());
    private final java.util.concurrent.atomic.AtomicBoolean failRunningPatch = new java.util.concurrent.atomic.AtomicBoolean(false);
    private final java.util.concurrent.atomic.AtomicBoolean failExitPatch = new java.util.concurrent.atomic.AtomicBoolean(false);
    private PipelinePatchResponse pipelinePatch(PipelinePatchRequest request) {
        pipelinePatchCalls.add(request);
        if (this.failRunningPatch.get() && request.payload().exit() == null) {
            return PipelinePatchResponse.builder()
                    .status500(status -> status.payload(error -> error.code(org.codingmatters.poom.ci.pipeline.api.types.Error.Code.UNEXPECTED_ERROR)))
                    .build();
        }
        if (this.failExitPatch.get() && request.payload().exit() != null) {
            return PipelinePatchResponse.builder()
                    .status500(status -> status.payload(error -> error.code(org.codingmatters.poom.ci.pipeline.api.types.Error.Code.UNEXPECTED_ERROR)))
                    .build();
        }
        return PipelinePatchResponse.builder()
                .status200(status -> status.payload(pipe -> pipe.status(st -> st.run(org.codingmatters.poom.ci.pipeline.api.types.pipeline.Status.Run.DONE))))
                .build();
    }

    private final List<PipelineStagesPostRequest> stagePostCalls = Collections.synchronizedList(new LinkedList<>());
    private PipelineStagesPostResponse stagePost(PipelineStagesPostRequest request) {
        stagePostCalls.add(request);
        return PipelineStagesPostResponse.builder()
                .status201(status -> status.xEntityId(request.payload().name()))
                .build();
    }

    private final List<PipelineStagePatchRequest> stagePatchCalls = Collections.synchronizedList(new LinkedList<>());
    private PipelineStagePatchResponse stagePatch(PipelineStagePatchRequest request) {
        stagePatchCalls.add(request);
        return PipelineStagePatchResponse.builder()
                .status200(status -> status.payload(stage -> stage
                        .name(request.stageName())
                        .status(st -> st.run(StageStatus.Run.DONE).exit(StageStatus.Exit.valueOf(request.payload().exit().name())))))
                .build();
    }

    private final List<PipelineStageLogsPatchRequest> logsPatchCalls = Collections.synchronizedList(new LinkedList<>());
    private PipelineStageLogsPatchResponse logsPatch(PipelineStageLogsPatchRequest request) {
        logsPatchCalls.add(request);
        return PipelineStageLogsPatchResponse.builder()
                .status201(status -> status.location(""))
                .build();
    }

    @Rule
    public TemporaryFolder dir = new TemporaryFolder();

    private final List<PipelineTrigger> contextProviderCalls = Collections.synchronizedList(new LinkedList<>());
    private PipelineContext.PipelineContextProvider testContextProvider = (pipelineId, trigger) -> {
        contextProviderCalls.add(trigger);

        PipelineVariables vars = PipelineVariables.builder()
                .pipelineId(pipelineId)
                .repository("repo")
                .branch("master")
                .changeset("1234567890")
                .build();

        return new PipelineContext(vars, Pipeline.builder()
                .stages(
                        stage -> stage.name("stage1"),
                        stage -> stage.name("stage2")
                )
                .build(), dir.newFolder(), dir.newFolder());
    };

    private final AtomicInteger execInitCount = new AtomicInteger(0);
    private final List<String> executedStages = Collections.synchronizedList(new LinkedList<>());
    private PipelineExecutor testExecutor(PipelineContext context) {
        return new PipelineExecutor() {
            @Override
            public void initialize() throws IOException {
                execInitCount.incrementAndGet();
            }

            @Override
            public boolean isExecutable(StageHolder stage) throws InvalidStageRestrictionException {
                return true;
            }

            @Override
            public StageTermination.Exit execute(StageHolder stage, StageLogListener logListener) throws IOException {
                executedStages.add(stage.stage().name());
                for (int i = 0; i < 3; i++) {
                    System.out.println("logging  :: " + stage.stage().name() + " log " + (i+1));
                    logListener.logLine(stage.stage().name() + " log " + (i+1));
                }
                return StageTermination.Exit.SUCCESS;
            }
        };
    }

    @Test
    public void nominalLogic() throws Exception {
        EnvProvider.provider(s -> {
            if("LOG_TO_PIPELINE".equals(s)) {
                return "true";
            } else {
                return EnvProvider.DEFAULT.apply(s);
            }
        });
        try {
            Job job = Job.builder()
                    .category("poom-ci").name("pipeline").arguments("pipeline-id")
                    .status(status -> status.run(Status.Run.RUNNING))
                    .processing(processing -> processing.submitted(LocalDateTime.now(ZoneOffset.UTC.normalized()).minus(1, ChronoUnit.MINUTES)))
                    .build();

            job = new PipelineJobProcessor(job, this.testContextProvider, this::testExecutor, this.pipelineClient).process();

            assertThat(this.pipelineGetCalls, hasSize(1));
            assertThat(this.contextProviderCalls, hasSize(1));

            assertThat(this.execInitCount.get(), is(1));
            assertThat(this.executedStages, contains("stage1", "stage2"));

            assertThat(this.stagePostCalls, hasSize(2));
            assertThat(this.stagePostCalls.get(0).payload().name(), is("stage1"));
            assertThat(this.stagePostCalls.get(0).stageType(), is(Stage.StageType.MAIN.name()));
            assertThat(this.stagePostCalls.get(1).payload().name(), is("stage2"));
            assertThat(this.stagePostCalls.get(1).stageType(), is(Stage.StageType.MAIN.name()));

            assertThat(this.stagePatchCalls, hasSize(2));
            assertThat(this.stagePatchCalls.get(0).stageName(), is("stage1"));
            assertThat(this.stagePostCalls.get(0).stageType(), is(Stage.StageType.MAIN.name()));
            assertThat(this.stagePatchCalls.get(1).stageName(), is("stage2"));
            assertThat(this.stagePostCalls.get(1).stageType(), is(Stage.StageType.MAIN.name()));

            assertThat(this.pipelinePatchCalls, hasSize(2));
            assertThat(this.pipelinePatchCalls.get(0).pipelineId(), is("pipeline-id"));
            assertThat(this.pipelinePatchCalls.get(0).payload(), is(PipelineTermination.builder().run(PipelineTermination.Run.RUNNING).build()));
            assertThat(this.pipelinePatchCalls.get(1).pipelineId(), is("pipeline-id"));
            assertThat(this.pipelinePatchCalls.get(1).payload(), is(PipelineTermination.builder().exit(PipelineTermination.Exit.SUCCESS).build()));

            Eventually.timeout(30, TimeUnit.SECONDS).assertThat(() -> {
                List<String> logs = new LinkedList<>();
                this.logsPatchCalls.stream().map(request -> request.payload().stream().map(line -> line.content()).collect(Collectors.toList())).forEach(line -> logs.addAll(line));

                System.out.println(logs);
                return logs;
            }, contains("stage1 log 1", "stage1 log 2", "stage1 log 3", "stage2 log 1", "stage2 log 2", "stage2 log 3"));

            assertThat(job.status().run(), is(Status.Run.DONE));
            assertThat(job.status().exit(), is(Status.Exit.SUCCESS));
        } finally {
            EnvProvider.reset();
        }
    }

    private Job job() {
        return Job.builder()
                .category("poom-ci").name("pipeline").arguments("pipeline-id")
                .status(status -> status.run(Status.Run.RUNNING))
                .processing(processing -> processing.submitted(LocalDateTime.now(ZoneOffset.UTC.normalized()).minus(1, ChronoUnit.MINUTES)))
                .build();
    }

    private final PipelineContext.PipelineContextProvider noDescriptorProvider = (pipelineId, trigger) -> {
        throw new NotAPipelineContextException("/workspace/poom-ci-pipeline.yaml");
    };

    private final PipelineContext.PipelineContextProvider failingProvider = (pipelineId, trigger) -> {
        throw new IOException("failed checking out a deleted branch");
    };

    @Test
    public void givenNoPipelineDescriptor__thenPipelineTerminatedWithSuccess_andJobDoneSuccess() throws Exception {
        Job job = new PipelineJobProcessor(this.job(), this.noDescriptorProvider, this::testExecutor, this.pipelineClient).process();

        assertThat(this.pipelinePatchCalls, hasSize(2));
        assertThat(this.pipelinePatchCalls.get(0).payload().run(), is(PipelineTermination.Run.RUNNING));
        assertThat(this.pipelinePatchCalls.get(1).pipelineId(), is("pipeline-id"));
        assertThat(this.pipelinePatchCalls.get(1).payload().exit(), is(PipelineTermination.Exit.SUCCESS));
        assertThat(job.status().run(), is(Status.Run.DONE));
        assertThat(job.status().exit(), is(Status.Exit.SUCCESS));
        assertThat(job.result(), is("not a pipeline context"));
        assertThat(this.stagePostCalls, hasSize(0));
    }

    @Test
    public void givenContextInitializationFailure__thenPipelineTerminatedWithFailure_andJobProcessingExceptionThrown() {
        try {
            new PipelineJobProcessor(this.job(), this.failingProvider, this::testExecutor, this.pipelineClient).process();
            org.junit.Assert.fail("expected a JobProcessingException");
        } catch (JobProcessingException e) {
            // attendu
        }

        assertThat(this.pipelinePatchCalls, hasSize(2));
        assertThat(this.pipelinePatchCalls.get(1).payload().exit(), is(PipelineTermination.Exit.FAILURE));
        assertThat(this.stagePostCalls, hasSize(0));
    }

    @Test
    public void givenTerminationPatchFails__thenOriginalOutcomeKept() throws Exception {
        this.failExitPatch.set(true);

        Job job = new PipelineJobProcessor(this.job(), this.noDescriptorProvider, this::testExecutor, this.pipelineClient).process();

        assertThat(this.pipelinePatchCalls, hasSize(2));
        assertThat(job.status().exit(), is(Status.Exit.SUCCESS));
    }

    @Test
    public void givenRunningStatusCannotBeSet__thenPipelineIsNotTerminated() {
        this.failRunningPatch.set(true);
        try {
            new PipelineJobProcessor(this.job(), this.noDescriptorProvider, this::testExecutor, this.pipelineClient).process();
            org.junit.Assert.fail("expected a JobProcessingException");
        } catch (JobProcessingException e) {
            // attendu
        }

        assertThat(this.pipelinePatchCalls, hasSize(1));
        assertThat(this.contextProviderCalls, hasSize(0));
    }

    @Test
    public void givenContextProviderRuntimeFailure__thenPipelineTerminatedWithFailure_andExceptionRethrown() throws Exception {
        PipelineContext.PipelineContextProvider brokenProvider = (pipelineId, trigger) -> {
            throw new ArrayIndexOutOfBoundsException("malformed checkout spec");
        };
        try {
            new PipelineJobProcessor(this.job(), brokenProvider, this::testExecutor, this.pipelineClient).process();
            org.junit.Assert.fail("expected an ArrayIndexOutOfBoundsException");
        } catch (ArrayIndexOutOfBoundsException e) {
            // attendu
        }

        assertThat(this.pipelinePatchCalls, hasSize(2));
        assertThat(this.pipelinePatchCalls.get(1).payload().exit(), is(PipelineTermination.Exit.FAILURE));
    }
}

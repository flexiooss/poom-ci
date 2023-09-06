package org.codingmatters.poom.ci.service.bundle;

import com.fasterxml.jackson.core.JsonFactory;
import io.flexio.services.support.mondo.MongoProvider;
import io.undertow.Undertow;
import org.codingmatters.poom.containers.ApiContainerRuntime;
import org.codingmatters.poom.containers.ApiContainerRuntimeBuilder;
import org.codingmatters.poom.containers.runtime.netty.NettyApiContainerRuntime;
import org.codingmatters.poom.containers.runtime.undertow.UndertowApiContainerRuntime;
import org.codingmatters.poom.services.domain.property.query.PropertyQuery;
import org.codingmatters.poomjobs.client.PoomjobsJobRegistryAPIClient;
import org.codingmatters.poomjobs.client.PoomjobsRunnerAPIClient;
import org.codingmatters.poomjobs.client.PoomjobsRunnerRegistryAPIClient;
import org.codingmatters.poomjobs.client.PoomjobsRunnerRegistryAPIHandlersClient;
import org.codingmatters.poom.poomjobs.domain.jobs.repositories.JobRepository;
import org.codingmatters.poom.poomjobs.domain.runners.repositories.RunnerRepository;
import org.codingmatters.poom.poomjobs.domain.values.jobs.JobQuery;
import org.codingmatters.poom.poomjobs.domain.values.jobs.JobValue;
import org.codingmatters.poom.poomjobs.domain.values.runners.RunnerQuery;
import org.codingmatters.poom.poomjobs.domain.values.runners.RunnerValue;
import org.codingmatters.poom.runner.manager.DefaultRunnerClientFactory;
import org.codingmatters.poom.runner.manager.RunnerInvokerListener;
import org.codingmatters.poom.services.domain.repositories.Repository;
import org.codingmatters.poom.services.logging.CategorizedLogger;
import org.codingmatters.poom.services.support.Env;
import org.codingmatters.poomjobs.service.PoomjobsJobRegistryAPI;
import org.codingmatters.poomjobs.service.PoomjobsRunnerRegistryAPI;
import org.codingmatters.poomjobs.service.api.PoomjobsJobRegistryAPIProcessor;
import org.codingmatters.poomjobs.service.api.PoomjobsRunnerRegistryAPIProcessor;
import org.codingmatters.rest.api.Api;
import org.codingmatters.rest.api.Processor;
import org.codingmatters.rest.api.client.okhttp.HttpClientWrapper;
import org.codingmatters.rest.api.client.okhttp.OkHttpClientWrapper;
import org.codingmatters.rest.api.processors.MatchingPathProcessor;
import org.codingmatters.rest.undertow.CdmHttpUndertowHandler;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

public class PoomCIApisService {
    static private final CategorizedLogger log = CategorizedLogger.getLogger(PoomCIApisService.class);

    public static void main(String[] args) {
        String host = Env.mandatory(Env.SERVICE_HOST).asString();
        int port = Env.mandatory(Env.SERVICE_PORT).asInteger();
        int clientPoolSize = Env.optional("CLIENT_POOL_SIZE").orElse(new Env.Var("5")).asInteger();

        JsonFactory jsonFactory = new JsonFactory();

        AtomicInteger threadIndex = new AtomicInteger(1);
        ExecutorService clientPool = Executors.newFixedThreadPool(clientPoolSize,
                runnable -> new Thread(runnable, "client-pool-thread-" + threadIndex.getAndIncrement())
        );

        PoomjobsRunnerRegistryAPI runnerRegistryAPI = runnerRegistryAPI(jsonFactory);
        PoomjobsRunnerRegistryAPIHandlersClient runnerRegistryClient = new PoomjobsRunnerRegistryAPIHandlersClient(
                runnerRegistryAPI.handlers(),
                clientPool
        );

        ApiContainerRuntime runtime;
        if(Env.optional("RUNTIME").orElse(new Env.Var(NettyApiContainerRuntime.class.getName())).asString().equals(NettyApiContainerRuntime.class.getName())) {
            runtime = new NettyApiContainerRuntime(host, port, log);
        } else {
            runtime = new UndertowApiContainerRuntime(host, port, log);
        }
        runtime = new ApiContainerRuntimeBuilder()
                .withApi(jobRegistryAPI(runnerRegistryClient, clientPool, jsonFactory, OkHttpClientWrapper.build()))
                .withApi(runnerRegistryAPI)
                .build(runtime);

        log.info("poom-ci pipeline api service running");
        runtime.main();
    }

    static public PoomjobsRunnerRegistryAPI runnerRegistryAPI(JsonFactory jsonFactory) {
        Repository<RunnerValue, RunnerQuery> runnerRepository;
        if(MongoProvider.isAvailable()) {
            runnerRepository = RunnerRepository.createMongo(
                    MongoProvider.fromEnv(),
                    Env.optional("JOBS_DATABASE").orElse(new Env.Var("ci_jobs")).asString()
            );
        } else {
            runnerRepository = RunnerRepository.createInMemory();
        }
        return new PoomjobsRunnerRegistryAPI(runnerRepository, jsonFactory);
    }

    static public PoomjobsJobRegistryAPI jobRegistryAPI(
            PoomjobsRunnerRegistryAPIClient runnerClient,
            ExecutorService clientPool, JsonFactory jsonFactory, HttpClientWrapper client) {
        Repository<JobValue, PropertyQuery> jobRepository;
        if(MongoProvider.isAvailable()) {
            jobRepository = JobRepository.createMongo(
                    MongoProvider.fromEnv(),
                    Env.optional("JOBS_DATABASE").orElse(new Env.Var("ci_jobs")).asString()
            );
        } else {
            jobRepository = JobRepository.createInMemory();
        }

        ExecutorService listenerPool = Executors.newFixedThreadPool(5);
        return new PoomjobsJobRegistryAPI(
                jobRepository,
                new RunnerInvokerListener(runnerClient, new DefaultRunnerClientFactory(jsonFactory, client), listenerPool),
                null,
                jsonFactory
        );
    }
}

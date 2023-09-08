package org.codingmatters.poom.ci.pipeline.api.service;

import com.fasterxml.jackson.core.JsonFactory;
import com.mongodb.client.MongoClient;
import io.flexio.io.mongo.repository.MongoCollectionRepository;
import io.flexio.services.support.mondo.MongoProvider;
import io.undertow.Undertow;
import org.codingmatters.poom.ci.pipeline.api.service.repository.LogStore;
import org.codingmatters.poom.ci.pipeline.api.service.repository.PoomCIRepository;
import org.codingmatters.poom.ci.pipeline.api.service.repository.logs.RepositoryLogStore;
import org.codingmatters.poom.ci.pipeline.api.service.storage.PipelineStage;
import org.codingmatters.poom.ci.pipeline.api.service.storage.StageLog;
import org.codingmatters.poom.ci.pipeline.api.service.storage.mongo.PipelineStageMongoMapper;
import org.codingmatters.poom.ci.pipeline.api.service.storage.mongo.StageLogMongoMapper;
import org.codingmatters.poom.ci.pipeline.api.types.Pipeline;
import org.codingmatters.poom.ci.pipeline.api.types.mongo.PipelineMongoMapper;
import org.codingmatters.poom.ci.triggers.GithubPushEvent;
import org.codingmatters.poom.ci.triggers.UpstreamBuild;
import org.codingmatters.poom.ci.triggers.mongo.GithubPushEventMongoMapper;
import org.codingmatters.poom.ci.triggers.mongo.UpstreamBuildMongoMapper;
import org.codingmatters.poom.containers.ApiContainerRuntime;
import org.codingmatters.poom.containers.ApiContainerRuntimeBuilder;
import org.codingmatters.poom.containers.runtime.netty.NettyApiContainerRuntime;
import org.codingmatters.poom.services.domain.property.query.PropertyQuery;
import org.codingmatters.poom.services.domain.repositories.Repository;
import org.codingmatters.poom.services.logging.CategorizedLogger;
import org.codingmatters.poom.services.support.Env;
import org.codingmatters.poomjobs.client.PoomjobsJobRegistryAPIRequesterClient;
import org.codingmatters.rest.api.client.okhttp.OkHttpClientWrapper;
import org.codingmatters.rest.api.client.okhttp.OkHttpRequesterFactory;
import org.codingmatters.rest.undertow.CdmHttpUndertowHandler;

public class PoomCIPipelineService {
    static private final CategorizedLogger log = CategorizedLogger.getLogger(PoomCIPipelineService.class);
    private static final String PIPELINES_DB = "PIPELINES_DB";

    public static void main(String[] args) {
        String host = Env.mandatory(Env.SERVICE_HOST).asString();
        int port = Env.mandatory(Env.SERVICE_PORT).asInteger();

        MongoClient mongoClient = MongoProvider.fromEnv();
        String database = Env.mandatory(PIPELINES_DB).asString();

        try(RepositoryLogStore logStore = new RepositoryLogStore(stagelogRepository(mongoClient, database))) {
            PoomCIPipelineService service = new PoomCIPipelineService(api(logStore, mongoClient, database), port, host);
            service.runtime().main();
        } catch (Exception e) {
            log.error("error terminating log store", e);
            System.exit(5);
        }
        log.info("poom-ci pipeline api service stopped.");
        System.exit(0);
    }

    static public PoomCIPipelinesApi api(LogStore logStore, MongoClient mongoClient, String database) {
        JsonFactory jsonFactory = new JsonFactory();

        PoomCIRepository repository = new PoomCIRepository(
                logStore,
                pipelineRepository(mongoClient, database),
                githubPushEventRepository(mongoClient, database),
                upstreamBuildRepository(mongoClient, database),
                pipelineStageRepository(mongoClient, database)
        );
        String jobRegistryUrl = Env.mandatory("JOB_REGISTRY_URL").asString();
        return new PoomCIPipelinesApi(repository, "/pipelines", jsonFactory, new PoomjobsJobRegistryAPIRequesterClient(
                new OkHttpRequesterFactory(OkHttpClientWrapper.build(), () -> jobRegistryUrl), jsonFactory, jobRegistryUrl)
        );
    }

    private final ApiContainerRuntime runtime;

    public PoomCIPipelineService(PoomCIPipelinesApi api, int port, String host) {
        this.runtime = new ApiContainerRuntimeBuilder()
                .withApi(api)
                .build(new NettyApiContainerRuntime(host, port, log));
    }

    public ApiContainerRuntime runtime() {
        return runtime;
    }


    public static Repository<Pipeline, PropertyQuery> pipelineRepository(MongoClient mongoClient, String database) {
        PipelineMongoMapper mapper = new PipelineMongoMapper();

        return MongoCollectionRepository.<Pipeline, PropertyQuery>repository(database, "pipelines")
                .withToDocument(mapper::toDocument)
                .withToValue(mapper::toValue)
                .buildWithPropertyQuery(mongoClient);
    }

    public static Repository<GithubPushEvent, PropertyQuery> githubPushEventRepository(MongoClient mongoClient, String database) {
        GithubPushEventMongoMapper mapper = new GithubPushEventMongoMapper();

        return MongoCollectionRepository.<GithubPushEvent, PropertyQuery>repository(database, "githib_push_events")
                .withToDocument(mapper::toDocument)
                .withToValue(mapper::toValue)
                .buildWithPropertyQuery(mongoClient);
    }

    public static Repository<UpstreamBuild, PropertyQuery> upstreamBuildRepository(MongoClient mongoClient, String database) {
        UpstreamBuildMongoMapper mapper = new UpstreamBuildMongoMapper();

        return MongoCollectionRepository.<UpstreamBuild, PropertyQuery>repository(database, "upstream_builds")
                .withToDocument(mapper::toDocument)
                .withToValue(mapper::toValue)
                .buildWithPropertyQuery(mongoClient);
    }


    public static Repository<PipelineStage, PropertyQuery> pipelineStageRepository(MongoClient mongoClient, String database) {
        PipelineStageMongoMapper mapper = new PipelineStageMongoMapper();

        return MongoCollectionRepository.<PipelineStage, PropertyQuery>repository(database, "pipeline_stages")
                .withToDocument(mapper::toDocument)
                .withToValue(mapper::toValue)
                .buildWithPropertyQuery(mongoClient);
    }

    private static Repository<StageLog, PropertyQuery> stagelogRepository(MongoClient mongoClient, String database) {
        StageLogMongoMapper mapper = new StageLogMongoMapper();

        return MongoCollectionRepository.<StageLog, PropertyQuery>repository(database, "stage_logs")
                .withToDocument(mapper::toDocument)
                .withToValue(mapper::toValue)
                .buildWithPropertyQuery(mongoClient);
    }
}

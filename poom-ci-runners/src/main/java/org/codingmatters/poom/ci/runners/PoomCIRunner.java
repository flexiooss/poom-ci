package org.codingmatters.poom.ci.runners;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import org.codingmatters.poom.ci.pipeline.api.types.PipelineTrigger;
import org.codingmatters.poom.ci.pipeline.client.PoomCIPipelineAPIClient;
import org.codingmatters.poom.ci.pipeline.client.PoomCIPipelineAPIRequesterClient;
import org.codingmatters.poomjobs.client.PoomjobsJobRegistryAPIClient;
import org.codingmatters.poomjobs.client.PoomjobsJobRegistryAPIRequesterClient;
import org.codingmatters.poomjobs.client.PoomjobsRunnerRegistryAPIClient;
import org.codingmatters.poomjobs.client.PoomjobsRunnerRegistryAPIRequesterClient;
import org.codingmatters.poom.jobs.runner.service.RunnerService;
import org.codingmatters.poom.jobs.runner.service.exception.RunnerServiceInitializationException;
import org.codingmatters.poom.services.logging.CategorizedLogger;
import org.codingmatters.poom.services.support.Env;
import org.codingmatters.rest.api.client.okhttp.OkHttpClientWrapper;
import org.codingmatters.rest.api.client.okhttp.OkHttpRequesterFactory;

import java.io.FileInputStream;
import java.io.IOException;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.cert.CertificateException;

public class PoomCIRunner {
    static private final CategorizedLogger log = CategorizedLogger.getLogger(PoomCIRunner.class);


    public static void main(String[] args) {
        KeyStore keystore = null;
        try {
            keystore = KeyStore.getInstance("pkcs12");
            keystore.load(new FileInputStream(Env.mandatory("KS").asString()), Env.mandatory("KS_PASS").asString().toCharArray());
        } catch (KeyStoreException | CertificateException | NoSuchAlgorithmException | IOException e) {
            log.error("error reading keystore poom-ci-runner", e);
            System.exit(1);
        }

        RunnerService runner = new PoomCIRunner(
                Env.mandatory("JOB_REGISTRY_URL").asString(),
                Env.mandatory("RUNNER_REGISTRY_URL").asString(),
                Env.mandatory("PIPELINE_URL").asString(),
                Env.mandatory(Env.SERVICE_HOST).asString(),
                Env.mandatory(Env.SERVICE_PORT).asInteger(),
                2,
                keystore, Env.mandatory("KS_KEY_PASS").asString().toCharArray()
        ).buildRunner();

        log.info("poom-ci runner starting");
        try {
            // bloquant jusqu'à l'arrêt du runner
            runner.run();
        } catch (RunnerServiceInitializationException e) {
            log.error("error starting poom-ci-runner", e);
            System.exit(1);
        } finally {
            runner.stop();
        }
        log.info("poom-ci runner stopped");
    }

    private final String jobRegistryUrl;
    private final String runnerRegistryUrl;
    private final String pipelineUrl;
    private final String host;
    private final Integer port;
    private final int workerPoolSize;
    private final KeyStore keystore;
    private final char[] keypass;

    public PoomCIRunner(String jobRegistryUrl, String runnerRegistryUrl, String pipelineUrl, String host, Integer port, int workerPoolSize, KeyStore keystore, char[] keypass) {
        this.jobRegistryUrl = jobRegistryUrl;
        this.runnerRegistryUrl = runnerRegistryUrl;
        this.pipelineUrl = pipelineUrl;
        this.host = host;
        this.port = port;
        this.workerPoolSize = workerPoolSize;
        this.keystore = keystore;
        this.keypass = keypass;
    }

    private RunnerService buildRunner() {
        JsonFactory jsonFactory = new JsonFactory();
        YAMLFactory yamlFactory = new YAMLFactory();

        PoomjobsJobRegistryAPIClient jobRegistryAPIClient = new PoomjobsJobRegistryAPIRequesterClient(
                new OkHttpRequesterFactory(OkHttpClientWrapper.build(), () -> this.jobRegistryUrl),
                jsonFactory,
                this.jobRegistryUrl
        );

        PoomjobsRunnerRegistryAPIClient runnerRegistryApi = new PoomjobsRunnerRegistryAPIRequesterClient(
                new OkHttpRequesterFactory(OkHttpClientWrapper.build(), () -> this.runnerRegistryUrl),
                jsonFactory,
                this.runnerRegistryUrl
        );

        PoomCIPipelineAPIClient pipelineAPIClient = new PoomCIPipelineAPIRequesterClient(
                new OkHttpRequesterFactory(OkHttpClientWrapper.build(), () -> this.pipelineUrl),
                jsonFactory,
                this.pipelineUrl
        );
        if (!Env.optional(Env.SERVICE_URL).isPresent()) {
            // RunnerService lit l'URL de callback dans SERVICE_URL ; GenericRunner la dérivait de l'hôte et du port.
            System.setProperty("service.url", String.format("http://%s:%s", this.host, this.port));
        }

        return RunnerService.setup()
                .jobs("poom-ci", new String[]{
                        PoomCIJobProcessorFactory.triggerJobName(PipelineTrigger.Type.GITHUB_PUSH),
                        PoomCIJobProcessorFactory.triggerJobName(PipelineTrigger.Type.UPSTREAM_BUILD)
                }, new PoomCIJobProcessorFactory(
                        pipelineAPIClient,
                        yamlFactory,
                        this.keystore,
                        this.keypass,
                        jsonFactory))
                .clients(runnerRegistryApi, jobRegistryAPIClient)
                .concurrency(this.workerPoolSize)
                .endpoint(this.host, this.port)
                .ttl(2 * 60 * 1000L)
                .build();
    }
}

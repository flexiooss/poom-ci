package org.codingmatters.poom.ci.github.webhook;

import com.fasterxml.jackson.core.JsonFactory;
import io.undertow.Handlers;
import io.undertow.Undertow;
import io.undertow.server.handlers.PathHandler;
import org.codingmatters.poom.ci.github.webhook.api.GithubWebhookAPIHandlers;
import org.codingmatters.poom.ci.github.webhook.api.service.GithubWebhookAPIProcessor;
import org.codingmatters.poom.ci.github.webhook.handlers.GithubWebhook;
import org.codingmatters.poom.ci.pipeline.client.PoomCIPipelineAPIClient;
import org.codingmatters.poom.ci.pipeline.client.PoomCIPipelineAPIRequesterClient;
import org.codingmatters.poom.containers.ApiContainerRuntime;
import org.codingmatters.poom.containers.ApiContainerRuntimeBuilder;
import org.codingmatters.poom.containers.runtime.netty.NettyApiContainerRuntime;
import org.codingmatters.poom.services.logging.CategorizedLogger;
import org.codingmatters.poom.services.support.Env;
import org.codingmatters.rest.api.RequestDelegate;
import org.codingmatters.rest.api.ResponseDelegate;
import org.codingmatters.rest.api.client.okhttp.OkHttpClientWrapper;
import org.codingmatters.rest.api.client.okhttp.OkHttpRequesterFactory;
import org.codingmatters.rest.undertow.CdmHttpUndertowHandler;

import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class GithubWebhookService {

    static private final CategorizedLogger log = CategorizedLogger.getLogger(GithubWebhookService.class);

    static public final String GITHUB_SECRET_TOKEN = "GITHUB_SECRET_TOKEN";
    static public final String PIPELINE_API_URL = "PIPELINE_API_URL";
    static private final String PREFIX_IGNORE = "PREFIX_IGNORE";

    private final String token;
    private final PathHandler handlers;
    private final JsonFactory jsonFactory;
    private final int port;
    private final ApiContainerRuntime runtime;

    private Undertow server;
    private final PoomCIPipelineAPIClient pipelineClient;
    private final String host;

    private static List<String> prefixToTignore;

    public static void main(String[] args) {
        String host = Env.mandatory(Env.SERVICE_HOST).asString();
        int port = Env.mandatory(Env.SERVICE_PORT).asInteger();
        String token = Env.mandatory(GITHUB_SECRET_TOKEN).asString();
        String pipelineUrl = Env.mandatory(PIPELINE_API_URL).asString();
        prefixToTignore = Env.optional(PREFIX_IGNORE).orElse(Env.Var.value("")).asList(";");

        JsonFactory jsonFactory = new JsonFactory();
        PoomCIPipelineAPIClient pipelineClient = new PoomCIPipelineAPIRequesterClient(
                new OkHttpRequesterFactory(OkHttpClientWrapper.build(), () -> pipelineUrl),
                jsonFactory,
                pipelineUrl);
        ApiContainerRuntime runtime = new GithubWebhookService(host, port, token, jsonFactory, pipelineClient).runtime();
        runtime.main();
    }

    public GithubWebhookService(String host, int port, String token, JsonFactory jsonFactory, PoomCIPipelineAPIClient pipelineClient) {
        this.host = host;
        this.port = port;
        this.token = token;
        this.jsonFactory = jsonFactory;
        this.pipelineClient = pipelineClient;
        this.handlers = Handlers.path();

        this.runtime  = new ApiContainerRuntimeBuilder()
                .apiProcessorWrapper(processor ->
                        new GithubWebhookGuard(
                                new GithubEventFilter(this::notImplementedEvent)
                                        .with("push", processor)
                                        .with("ping", this::pong),
                                this.token)
                )
                .withApi(new GithubWebhookApi(this.pipelineClient, prefixToTignore, this.jsonFactory))
                .build(new NettyApiContainerRuntime(host, port, log));
    }

    public ApiContainerRuntime runtime() {
        return this.runtime;
    }

    private void pong(RequestDelegate request, ResponseDelegate response) {
        log.audit().info("got a ping, issuing a pong : {}" , this.payloadAsString(request));
        response.status(200);
        response.contenType("text/plain");
        response.payload("pong", "UTF-8");
    }

    private void notImplementedEvent(RequestDelegate request, ResponseDelegate response) {
        String logToken = log.audit().tokenized().info("event {} not processed ; {}",
                request.headers().get(GithubEventFilter.EVENT_HEADER),
                this.payloadAsString(request)
                );

        response.status(501);
        response.contenType("text/plain");
        response.payload(String.format("event not implemented, see logs (token=%s)", logToken), "UTF-8");
    }

    private String payloadAsString(RequestDelegate request) {
        try {
            try(Reader payload = new InputStreamReader(request.payload())) {
                StringBuilder result = new StringBuilder();
                char[] buffer = new char[1024];
                for(int read = payload.read(buffer) ; read != -1 ; read = payload.read(buffer)) {
                    result.append(buffer, 0, read);
                }
                return result.toString();
            }
        } catch (IOException e) {
            return String.format(
                    "failed reading payload see logs (token=%s)",
                    log.tokenized().error("error reading request payload", e)
            );
        }
    }
}

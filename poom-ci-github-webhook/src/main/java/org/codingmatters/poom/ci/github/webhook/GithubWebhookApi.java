package org.codingmatters.poom.ci.github.webhook;

import com.fasterxml.jackson.core.JsonFactory;
import org.codingmatters.poom.ci.github.webhook.api.GithubWebhookAPIHandlers;
import org.codingmatters.poom.ci.github.webhook.api.service.GithubWebhookAPIProcessor;
import org.codingmatters.poom.ci.github.webhook.handlers.GithubWebhook;
import org.codingmatters.poom.ci.pipeline.client.PoomCIPipelineAPIClient;
import org.codingmatters.rest.api.Api;
import org.codingmatters.rest.api.Processor;

import java.util.List;

public class GithubWebhookApi implements Api {
    private static final String VERSION = Api.versionFrom(GithubWebhookApi.class);
    private final Processor processor;

    public GithubWebhookApi(PoomCIPipelineAPIClient pipelineClient, List<String> prefixToTignore, JsonFactory jsonFactory) {
        this.processor = new GithubWebhookAPIProcessor(
                this.path(),
                jsonFactory,
                new GithubWebhookAPIHandlers.Builder()
                        .webhookPostHandler(new GithubWebhook(pipelineClient, prefixToTignore))
                        .build()
        );
    }

    @Override
    public String name() {
        return "github-webhook";
    }

    @Override
    public String version() {
        return VERSION;
    }

    @Override
    public Processor processor() {
        return this.processor;
    }

    @Override
    public String path() {
        return "/github/webhook";
    }
}

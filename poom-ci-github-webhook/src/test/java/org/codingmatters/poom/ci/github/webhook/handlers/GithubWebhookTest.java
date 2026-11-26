package org.codingmatters.poom.ci.github.webhook.handlers;

import org.codingmatters.poom.ci.github.webhook.api.WebhookPostRequest;
import org.codingmatters.poom.ci.github.webhook.api.WebhookPostResponse;
import org.codingmatters.poom.ci.pipeline.api.GithubTriggersPostRequest;
import org.codingmatters.poom.ci.pipeline.api.GithubTriggersPostResponse;
import org.codingmatters.poom.ci.pipeline.api.PoomCIPipelineAPIHandlers;
import org.codingmatters.poom.ci.pipeline.client.PoomCIPipelineAPIHandlersClient;
import org.codingmatters.poom.ci.triggers.GithubPushEvent;
import org.junit.After;
import org.junit.Test;

import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;

public class GithubWebhookTest {

    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final List<GithubTriggersPostRequest> posted = Collections.synchronizedList(new LinkedList<>());

    private final GithubWebhook webhook = new GithubWebhook(new PoomCIPipelineAPIHandlersClient(new PoomCIPipelineAPIHandlers.Builder()
            .githubTriggersPostHandler(request -> {
                this.posted.add(request);
                return GithubTriggersPostResponse.builder().status201(st -> st.xEntityId("trigger").location("/triggers/git-hub/trigger")).build();
            })
            .build(), this.executor), List.of("ignored/"));

    @After
    public void tearDown() {
        this.executor.shutdownNow();
    }

    private static GithubPushEvent push(String ref, String after) {
        return GithubPushEvent.builder()
                .ref(ref)
                .before("1c27cced8346b554f3e320d587246d2098137333")
                .after(after)
                .repository(repository -> repository.name("flexio-api-gateway").full_name("Flexio-corp/flexio-api-gateway"))
                .build();
    }

    @Test
    public void givenBranchDeletion__thenIgnoredWith202_andNothingPosted() {
        WebhookPostResponse response = this.webhook.apply(WebhookPostRequest.builder()
                .payload(push("refs/heads/release/1.0.0", "0000000000000000000000000000000000000000"))
                .build());

        assertThat(response.opt().status202().isPresent(), is(true));
        assertThat(response.status202().payload(), is("Nothing to do : branch deleted !"));
        assertThat(this.posted, hasSize(0));
    }

    @Test
    public void givenPush__thenForwardedWith200() {
        WebhookPostResponse response = this.webhook.apply(WebhookPostRequest.builder()
                .payload(push("refs/heads/develop", "6b0841375120260a3a59ffb26b0841375120260a"))
                .build());

        assertThat(response.opt().status200().isPresent(), is(true));
        assertThat(this.posted, hasSize(1));
    }

    @Test
    public void givenIgnoredPrefix__thenStill202() {
        WebhookPostResponse response = this.webhook.apply(WebhookPostRequest.builder()
                .payload(push("refs/heads/ignored/x", "6b0841375120260a3a59ffb26b0841375120260a"))
                .build());

        assertThat(response.opt().status202().isPresent(), is(true));
        assertThat(this.posted, hasSize(0));
    }

    @Test
    public void isBranchDeletion__onlyForAnAllZeroAfter() {
        assertThat(GithubWebhook.isBranchDeletion(push("refs/heads/x", "0000000000000000000000000000000000000000")), is(true));
        assertThat(GithubWebhook.isBranchDeletion(push("refs/heads/x", "6b0841375120260a3a59ffb26b0841375120260a")), is(false));
        assertThat(GithubWebhook.isBranchDeletion(push("refs/heads/x", null)), is(false));
        assertThat(GithubWebhook.isBranchDeletion(push("refs/heads/x", "")), is(false));
    }
}

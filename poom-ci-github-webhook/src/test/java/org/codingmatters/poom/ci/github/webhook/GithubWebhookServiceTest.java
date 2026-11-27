package org.codingmatters.poom.ci.github.webhook;

import com.fasterxml.jackson.core.JsonFactory;
import org.codingmatters.poom.ci.pipeline.api.GithubTriggersPostRequest;
import org.codingmatters.poom.ci.pipeline.api.GithubTriggersPostResponse;
import org.codingmatters.poom.ci.pipeline.api.PoomCIPipelineAPIHandlers;
import org.codingmatters.poom.ci.pipeline.client.PoomCIPipelineAPIHandlersClient;
import org.codingmatters.poom.containers.ApiContainerRuntime;
import org.codingmatters.poom.containers.runtime.netty.NettyApiContainerRuntime;
import org.codingmatters.poom.containers.runtime.undertow.UndertowApiContainerRuntime;
import org.junit.After;
import org.junit.Test;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.net.ServerSocket;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.HexFormat;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;

public class GithubWebhookServiceTest {

    private static final String SECRET = "webhook-secret";

    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final List<GithubTriggersPostRequest> posted = Collections.synchronizedList(new LinkedList<>());
    private ApiContainerRuntime.Handle running;

    @After
    public void tearDown() throws Exception {
        if (this.running != null) {
            this.running.stop();
        }
        this.executor.shutdownNow();
    }

    @Test
    public void givenNoRuntimeName__thenUndertow() {
        assertThat(GithubWebhookService.containerRuntime(null, "localhost", 0), is(instanceOf(UndertowApiContainerRuntime.class)));
        assertThat(GithubWebhookService.containerRuntime(" ", "localhost", 0), is(instanceOf(UndertowApiContainerRuntime.class)));
    }

    @Test
    public void givenRuntimeName__thenThatRuntime_whateverTheCase() {
        assertThat(GithubWebhookService.containerRuntime("undertow", "localhost", 0), is(instanceOf(UndertowApiContainerRuntime.class)));
        assertThat(GithubWebhookService.containerRuntime("Netty", "localhost", 0), is(instanceOf(NettyApiContainerRuntime.class)));
    }

    @Test(expected = IllegalArgumentException.class)
    public void givenUnknownRuntimeName__thenRefused() {
        GithubWebhookService.containerRuntime("jetty", "localhost", 0);
    }

    @Test
    public void givenDefaultRuntime_whenSmallSignedPush__thenForwardedAndThanked() throws Exception {
        int port = freePort();
        this.running = new GithubWebhookService("localhost", port, SECRET, new JsonFactory(), new PoomCIPipelineAPIHandlersClient(
                new PoomCIPipelineAPIHandlers.Builder()
                        .githubTriggersPostHandler(request -> {
                            this.posted.add(request);
                            return GithubTriggersPostResponse.builder().status201(st -> st.xEntityId("trigger").location("/triggers/git-hub/trigger")).build();
                        })
                        .build(), this.executor), null).runtime().handle();
        this.running.start();

        String payload = "{\"ref\":\"refs/heads/develop\",\"before\":\"1c27cced8346b554f3e320d587246d2098137333\","
                + "\"after\":\"6b0841375120260a3a59ffb26b0841375120260a\","
                + "\"repository\":{\"name\":\"flexio-ci\",\"full_name\":\"Flexio-corp/flexio-ci\"}}";
        HttpResponse<String> response = HttpClient.newBuilder().version(HttpClient.Version.HTTP_1_1).build().send(HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/github/webhook/"))
                .header("content-type", "application/json")
                .header("X-GitHub-Event", "push")
                .header("X-Hub-Signature", "sha1=" + sign(payload))
                .POST(HttpRequest.BodyPublishers.ofString(payload))
                .build(), HttpResponse.BodyHandlers.ofString());

        assertThat(response.statusCode(), is(200));
        assertThat(response.body(), containsString("Thanks!"));
        assertThat(this.posted, hasSize(1));
        assertThat(this.posted.get(0).payload().ref(), is("refs/heads/develop"));
    }

    private static String sign(String payload) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA1");
        mac.init(new SecretKeySpec(SECRET.getBytes(StandardCharsets.UTF_8), "HmacSHA1"));
        return HexFormat.of().formatHex(mac.doFinal(payload.getBytes(StandardCharsets.UTF_8)));
    }

    private static int freePort() throws Exception {
        try (ServerSocket socket = new ServerSocket(0)) {
            return socket.getLocalPort();
        }
    }
}

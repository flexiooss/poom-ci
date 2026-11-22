package org.codingmatters.poom.ci.github.webhook;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import org.codingmatters.rest.api.RequestDelegate;
import org.codingmatters.rest.tests.api.TestRequestDeleguate;
import org.codingmatters.rest.tests.api.TestResponseDeleguate;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.slf4j.LoggerFactory;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;

public class GithubWebhookGuardTest {

    private static final String SECRET = "test-secret";
    private static final byte[] PAYLOAD = "{\"ref\":\"refs/heads/develop\",\"repository\":{\"name\":\"poom-ci\"}}"
            .getBytes(StandardCharsets.UTF_8);

    private final AtomicBoolean guardedCalled = new AtomicBoolean(false);
    private final ListAppender<ILoggingEvent> logs = new ListAppender<>();
    private final Logger rootLogger = (Logger) LoggerFactory.getLogger(org.slf4j.Logger.ROOT_LOGGER_NAME);

    @Before
    public void setUp() {
        this.logs.start();
        this.rootLogger.addAppender(this.logs);
    }

    @After
    public void tearDown() {
        this.rootLogger.detachAppender(this.logs);
    }

    @Test
    public void givenValidSha256Signature__whenProcessing__thenGuardedProcessorIsCalled() throws Exception {
        TestResponseDeleguate response = this.process(SECRET, PAYLOAD, "X-Hub-Signature-256", "sha256=" + hmac("HmacSHA256", SECRET, PAYLOAD));

        assertThat(this.guardedCalled.get(), is(true));
        assertThat(response.status(), is(200));
    }

    @Test
    public void givenWrongSha256Signature__whenProcessing__thenRefusedWith403() throws Exception {
        TestResponseDeleguate response = this.process(SECRET, PAYLOAD, "X-Hub-Signature-256", "sha256=" + hmac("HmacSHA256", "other-secret", PAYLOAD));

        assertThat(this.guardedCalled.get(), is(false));
        assertThat(response.status(), is(403));
    }

    @Test
    public void givenNoSignatureHeader__whenProcessing__thenRefusedWith403() throws Exception {
        TestResponseDeleguate response = this.process(SECRET, PAYLOAD);

        assertThat(this.guardedCalled.get(), is(false));
        assertThat(response.status(), is(403));
    }

    @Test
    public void givenOnlyValidSha1Signature__whenProcessing__thenGuardedProcessorIsCalled() throws Exception {
        TestResponseDeleguate response = this.process(SECRET, PAYLOAD, "X-Hub-Signature", "sha1=" + hmac("HmacSHA1", SECRET, PAYLOAD));

        assertThat(this.guardedCalled.get(), is(true));
        assertThat(response.status(), is(200));
    }

    @Test
    public void givenValidSha1ButWrongSha256Signature__whenProcessing__thenSha256PrevailsAndRequestIsRefused() throws Exception {
        TestResponseDeleguate response = this.process(SECRET, PAYLOAD,
                "X-Hub-Signature", "sha1=" + hmac("HmacSHA1", SECRET, PAYLOAD),
                "X-Hub-Signature-256", "sha256=" + hmac("HmacSHA256", "other-secret", PAYLOAD));

        assertThat(this.guardedCalled.get(), is(false));
        assertThat(response.status(), is(403));
    }

    @Test
    public void givenPayloadThatIsNotValidUtf8__whenSignatureComputedOnRawBytes__thenGuardedProcessorIsCalled() throws Exception {
        byte[] payload = new byte[]{'{', '"', 'a', '"', ':', '"', (byte) 0xC3, (byte) 0x28, (byte) 0xFF, '"', '}'};

        TestResponseDeleguate response = this.process(SECRET, payload, "X-Hub-Signature-256", "sha256=" + hmac("HmacSHA256", SECRET, payload));

        assertThat(this.guardedCalled.get(), is(true));
        assertThat(response.status(), is(200));
    }

    @Test
    public void givenWrongSignatures__whenRefused__thenExpectedSignaturesAreNeverLogged() throws Exception {
        String expectedSha256 = hmac("HmacSHA256", SECRET, PAYLOAD);
        String expectedSha1 = hmac("HmacSHA1", SECRET, PAYLOAD);

        this.process(SECRET, PAYLOAD, "X-Hub-Signature-256", "sha256=" + hmac("HmacSHA256", "other-secret", PAYLOAD));
        this.process(SECRET, PAYLOAD, "X-Hub-Signature", "sha1=" + hmac("HmacSHA1", "other-secret", PAYLOAD));

        assertThat(this.logs.list, is(not(empty())));
        for (ILoggingEvent event : this.logs.list) {
            assertThat(event.getFormattedMessage(), not(containsString(expectedSha256)));
            assertThat(event.getFormattedMessage(), not(containsString(expectedSha1)));
        }
    }

    @Test
    public void givenEmptySecret__whenProcessingAnySignature__thenRefusedWith403() throws Exception {
        TestResponseDeleguate response = this.process("", PAYLOAD, "X-Hub-Signature-256", "sha256=" + hmac("HmacSHA256", "any-secret", PAYLOAD));

        assertThat(this.guardedCalled.get(), is(false));
        assertThat(response.status(), is(403));
    }

    private TestResponseDeleguate process(String secret, byte[] payload, String... headerNamesAndValues) throws Exception {
        GithubWebhookGuard guard = new GithubWebhookGuard((request, response) -> {
            this.guardedCalled.set(true);
            response.status(200);
        }, secret);

        TestRequestDeleguate.Builder builder = TestRequestDeleguate.request(RequestDelegate.Method.POST, "/github/webhook")
                .contentType("application/json")
                .payload(new ByteArrayInputStream(payload))
                .addHeader("X-GitHub-Event", "push");
        for (int i = 0; i < headerNamesAndValues.length; i += 2) {
            builder.addHeader(headerNamesAndValues[i], headerNamesAndValues[i + 1]);
        }

        TestResponseDeleguate response = new TestResponseDeleguate();
        guard.process(builder.build(), response);
        return response;
    }

    private static String hmac(String algorithm, String secret, byte[] payload) throws Exception {
        Mac mac = Mac.getInstance(algorithm);
        mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), algorithm));
        StringBuilder hex = new StringBuilder();
        for (byte b : mac.doFinal(payload)) {
            hex.append(String.format("%02x", b));
        }
        return hex.toString();
    }
}

package org.codingmatters.poom.ci.github.webhook;

import org.codingmatters.poom.services.logging.CategorizedLogger;
import org.codingmatters.rest.api.Processor;
import org.codingmatters.rest.api.RequestDelegate;
import org.codingmatters.rest.api.ResponseDelegate;
import org.codingmatters.rest.api.processors.GuardedProcessor;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.util.List;
import java.util.Map;

/**
 * Checks the GitHub webhook signature before letting the request through.
 * <p>
 * X-Hub-Signature-256 (HMAC-SHA256) is checked when present ; X-Hub-Signature (HMAC-SHA1) is only used as a fallback
 * when the SHA-256 header is absent. The HMAC is computed on the raw payload bytes and compared in constant time.
 * Expected signatures are never logged : with them, anyone able to read the logs could forge a valid request for
 * the payload they sent.
 * <p>
 * The payload is read once and handed to the guarded processor from memory : the runtime's request delegate cannot be
 * read twice for small payloads.
 */
public class GithubWebhookGuard extends GuardedProcessor {

    static private final CategorizedLogger log = CategorizedLogger.getLogger(GithubWebhookGuard.class);

    static private final String SHA256_HEADER = "X-Hub-Signature-256";
    static private final String SHA1_HEADER = "X-Hub-Signature";

    private final byte[] secret;

    public GithubWebhookGuard(Processor guarded, String token) {
        super(guarded);
        this.secret = token == null ? new byte[0] : token.getBytes(StandardCharsets.UTF_8);
    }

    @Override
    public void process(RequestDelegate request, ResponseDelegate response) throws IOException {
        byte[] payload;
        try (InputStream in = request.payload()) {
            payload = in.readAllBytes();
        }
        super.process(new BufferedPayloadRequest(request, payload), response);
    }

    @Override
    protected boolean passed(RequestDelegate request, ResponseDelegate response) throws IOException {
        if (this.secret.length == 0) {
            this.errorResponse(response, log.tokenized().error("webhook secret is not configured, refusing request"));
            return false;
        }

        String sha256Signature = this.header(request, SHA256_HEADER);
        String sha1Signature = this.header(request, SHA1_HEADER);
        if (sha256Signature == null && sha1Signature == null) {
            this.errorResponse(response, log.tokenized().error("neither {} nor {} provided", SHA256_HEADER, SHA1_HEADER));
            return false;
        }

        try {
            byte[] payload = this.readPayload(request);
            boolean valid = sha256Signature != null
                    ? this.matches(sha256Signature, "sha256=", "HmacSHA256", payload)
                    : this.matches(sha1Signature, "sha1=", "HmacSHA1", payload);
            if (!valid) {
                this.errorResponse(response, log.tokenized().error("{} signature doesn't match",
                        sha256Signature != null ? SHA256_HEADER : SHA1_HEADER));
            }
            return valid;
        } catch (GeneralSecurityException e) {
            this.errorResponse(response, log.tokenized().error("error verifying signature", e));
            return false;
        }
    }

    private boolean matches(String provided, String prefix, String algorithm, byte[] payload) throws GeneralSecurityException {
        Mac mac = Mac.getInstance(algorithm);
        mac.init(new SecretKeySpec(this.secret, algorithm));
        byte[] expected = (prefix + toHexString(mac.doFinal(payload))).getBytes(StandardCharsets.US_ASCII);
        return MessageDigest.isEqual(expected, provided.getBytes(StandardCharsets.US_ASCII));
    }

    private String header(RequestDelegate request, String name) {
        List<String> values = request.headers().get(name);
        if (values == null || values.isEmpty() || values.get(0) == null || values.get(0).isEmpty()) {
            return null;
        }
        return values.get(0);
    }

    private byte[] readPayload(RequestDelegate request) throws IOException {
        try (InputStream payload = request.payload()) {
            return payload.readAllBytes();
        }
    }

    static private class BufferedPayloadRequest implements RequestDelegate {
        private final RequestDelegate delegate;
        private final byte[] payload;

        BufferedPayloadRequest(RequestDelegate delegate, byte[] payload) {
            this.delegate = delegate;
            this.payload = payload;
        }

        @Override
        public InputStream payload() {
            return new ByteArrayInputStream(this.payload);
        }

        @Override
        public String path() {
            return this.delegate.path();
        }

        @Override
        public java.util.regex.Matcher pathMatcher(String regex) {
            return this.delegate.pathMatcher(regex);
        }

        @Override
        public Method method() {
            return this.delegate.method();
        }

        @Override
        public String contentType() {
            return this.delegate.contentType();
        }

        @Override
        public Map<String, List<String>> uriParameters(String pathExpression) {
            return this.delegate.uriParameters(pathExpression);
        }

        @Override
        public Map<String, List<String>> queryParameters() {
            return this.delegate.queryParameters();
        }

        @Override
        public Map<String, List<String>> headers() {
            return this.delegate.headers();
        }

        @Override
        public String absolutePath(String relative) {
            return this.delegate.absolutePath(relative);
        }

        @Override
        public void close() throws Exception {
            this.delegate.close();
        }
    }

    private void errorResponse(ResponseDelegate response, String errorToken) {
        response.contenType("text/plain");
        response.status(403);
        response.payload(String.format("signature doesn't match, see logs (%s)", errorToken).getBytes(StandardCharsets.UTF_8));
    }

    private static String toHexString(byte[] bytes) {
        StringBuilder hex = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) {
            hex.append(String.format("%02x", b));
        }
        return hex.toString();
    }
}

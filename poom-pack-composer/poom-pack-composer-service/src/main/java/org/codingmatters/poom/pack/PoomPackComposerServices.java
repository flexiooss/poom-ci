package org.codingmatters.poom.pack;

import io.undertow.Undertow;
import org.codingmatters.poom.containers.ApiContainerRuntime;
import org.codingmatters.poom.containers.ApiContainerRuntimeBuilder;
import org.codingmatters.poom.containers.runtime.netty.NettyApiContainerRuntime;
import org.codingmatters.poom.services.logging.CategorizedLogger;
import org.codingmatters.poom.services.support.Env;

public class PoomPackComposerServices {

    static private final CategorizedLogger log = CategorizedLogger.getLogger( PoomPackComposerServices.class );
    private static final String REPOSITORY_PATH = "repository_path";
    private Undertow server;

    private final ApiContainerRuntime runtime;

    public PoomPackComposerServices( PoomPackComposerApi api, int port, String host ) {
        this.runtime = new ApiContainerRuntimeBuilder()
                .withApi(api)
                .build(new NettyApiContainerRuntime(host, port, log));
    }

    public ApiContainerRuntime runtime() {
        return this.runtime;
    }

    public static void main(String[] args ) {
        String host = Env.mandatory( Env.SERVICE_HOST ).asString();
        int port = Env.mandatory( Env.SERVICE_PORT ).asInteger();

        PoomPackComposerServices service = new PoomPackComposerServices(
                new PoomPackComposerApi(
                        Env.mandatory(REPOSITORY_PATH).asString(),
                        Env.mandatory(Env.SERVICE_URL).asString(),
                        Env.mandatory("API_KEY").asString()
                ),
                port, host
        );
        service.runtime().main();
    }

}


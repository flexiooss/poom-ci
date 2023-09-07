package org.codingmatters.poom.pack;

import com.fasterxml.jackson.core.JsonFactory;
import org.codingmatters.poom.ci.api.PoomPackComposerDescriptor;
import org.codingmatters.poom.ci.api.PoomPackComposerHandlers;
import org.codingmatters.poom.pack.handler.*;
import org.codingmatters.poom.ci.service.PoomPackComposerProcessor;
import org.codingmatters.poom.pack.handler.pack.JsonPackageBuilder;
import org.codingmatters.rest.api.Api;
import org.codingmatters.rest.api.Processor;

import java.io.File;


public class PoomPackComposerApi implements Api {
    private static final String VERSION = Api.versionFrom(PoomPackComposerApi.class);

    private final String name = "poom-pack-composer";
    private PoomPackComposerProcessor processor;

    public PoomPackComposerApi( String repositoryPath, String serviceUrl, String api_key ) {
        final File repository = new File( repositoryPath );
        PoomPackComposerHandlers handlers = new PoomPackComposerHandlers.Builder()
                .packagesGetHandler( new GetPackage( repository, serviceUrl ) )
                .repositoryPostHandler( new SavePackage( repositoryPath, api_key ) )
                .artifactsDeleteHandler( new DeleteArtifact( repositoryPath, api_key ) )
                .artifactsGetHandler( new GetArtifact( repositoryPath ) )
                .build();
        this.processor = new PoomPackComposerProcessor( this.path(), new JsonFactory(), handlers );
    }

    @Override
    public String name() {
        return this.name;
    }

    @Override
    public String version() {
        return VERSION;
    }

    public Processor processor() {
        return this.processor;
    }

    @Override
    public String path() {
        return "/" + this.name;
    }

}


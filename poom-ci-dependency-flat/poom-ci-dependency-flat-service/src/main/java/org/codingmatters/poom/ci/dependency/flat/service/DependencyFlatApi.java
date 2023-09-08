package org.codingmatters.poom.ci.dependency.flat.service;

import com.fasterxml.jackson.core.JsonFactory;
import org.codingmatters.poom.ci.dependency.api.PoomCIDependencyAPIDescriptor;
import org.codingmatters.poom.ci.dependency.api.processor.PoomCIDependencyAPIProcessor;
import org.codingmatters.poom.ci.dependency.flat.GraphManager;
import org.codingmatters.poom.ci.dependency.flat.handlers.FlatDependencyHandlersBuilder;
import org.codingmatters.rest.api.Api;
import org.codingmatters.rest.api.Processor;

public class DependencyFlatApi implements Api {
    private static final String VERSION = Api.versionFrom(DependencyFlatApi.class);
    private final Processor processor;

    public DependencyFlatApi(GraphManager graphManager, JsonFactory jsonFactory) {
        this.processor = new PoomCIDependencyAPIProcessor(
                "/" + PoomCIDependencyAPIDescriptor.NAME,
                jsonFactory,
                new FlatDependencyHandlersBuilder(graphManager).build()
        );
    }

    @Override
    public String name() {
        return PoomCIDependencyAPIDescriptor.NAME;
    }

    @Override
    public String version() {
        return VERSION;
    }

    @Override
    public Processor processor() {
        return this.processor;
    }
}

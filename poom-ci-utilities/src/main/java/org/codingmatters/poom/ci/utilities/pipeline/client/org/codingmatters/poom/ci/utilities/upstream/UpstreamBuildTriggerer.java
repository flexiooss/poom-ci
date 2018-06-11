package org.codingmatters.poom.ci.utilities.pipeline.client.org.codingmatters.poom.ci.utilities.upstream;

import com.fasterxml.jackson.core.JsonFactory;
import org.codingmatters.poom.ci.dependency.api.ValueList;
import org.codingmatters.poom.ci.dependency.api.types.Repository;
import org.codingmatters.poom.ci.dependency.client.PoomCIDependencyAPIClient;
import org.codingmatters.poom.ci.dependency.client.PoomCIDependencyAPIRequesterClient;
import org.codingmatters.poom.ci.pipeline.client.PoomCIPipelineAPIClient;
import org.codingmatters.poom.ci.pipeline.client.PoomCIPipelineAPIRequesterClient;
import org.codingmatters.poom.ci.triggers.UpstreamBuild;
import org.codingmatters.poom.ci.triggers.upstreambuild.Downstream;
import org.codingmatters.poom.ci.triggers.upstreambuild.Upstream;
import org.codingmatters.rest.api.client.okhttp.OkHttpClientWrapper;
import org.codingmatters.rest.api.client.okhttp.OkHttpRequesterFactory;

import java.io.IOException;

public class UpstreamBuildTriggerer {


    public static void main(String[] args) {
        if(args.length < 5) {
            throw new RuntimeException("usage : <pipeline base url> <dependencies base url> <repository id> <repository name> <checkout spec>");
        }

        JsonFactory jsonFactory = new JsonFactory();
        OkHttpRequesterFactory requesterFactory = new OkHttpRequesterFactory(OkHttpClientWrapper.build());

        String pipelineApiUrl = args[0];
        PoomCIPipelineAPIClient pipelineAPIClient = new PoomCIPipelineAPIRequesterClient(
                requesterFactory,
                jsonFactory,
                pipelineApiUrl
        );
        String dependencyApiUrl = args[1];
        PoomCIDependencyAPIClient dependencyAPIClient = new PoomCIDependencyAPIRequesterClient(
                requesterFactory,
                jsonFactory,
                dependencyApiUrl
        );

        String repositoryId = args[2];
        String repositoryName = args[3];
        String checkoutSpec = args[4];

        Upstream upstream = Upstream.builder()
                .id(repositoryId)
                .name(repositoryName)
                .checkoutSpec(checkoutSpec)
                .build();

        ValueList<Repository> downstreamRepositories = null;
        try {
            downstreamRepositories = dependencyAPIClient.repositories().repository().repositoryDownstreamRepositories().get(req -> req.repositoryId(repositoryId)).opt().status200().payload().orElseThrow(() -> new RuntimeException("failed getting downstream repositories"));
        } catch (IOException e) {
            throw new RuntimeException("failed connecting to dependency api at " + dependencyApiUrl, e);
        }

        for (Repository downstreamRepository : downstreamRepositories) {
            try {
                Downstream downstream = Downstream.builder()
                        .id(downstreamRepository.id())
                        .name(downstreamRepository.name())
                        .checkoutSpec(downstreamRepository.checkoutSpec())
                        .build();

                UpstreamBuild upstreamBuild = UpstreamBuild.builder()
                        .upstream(upstream)
                        .downstream(downstream)
                        .build();
                pipelineAPIClient.triggers().upstreamBuildTriggers().post(req -> req.payload(upstreamBuild)).opt().status201().orElseThrow(() -> new RuntimeException("failed triggering downstream build"));
            } catch (IOException e) {
                throw new RuntimeException("failed connecting to pipeline api at " + pipelineApiUrl, e);
            }
        }

    }
}
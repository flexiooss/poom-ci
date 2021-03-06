package org.codingmatters.poom.ci.dependency.flat.handlers;

import org.codingmatters.poom.ci.dependency.api.RepositoryDownstreamRepositoriesGetRequest;
import org.codingmatters.poom.ci.dependency.api.RepositoryDownstreamRepositoriesGetResponse;
import org.codingmatters.poom.ci.dependency.api.repositorydownstreamrepositoriesgetresponse.Status200;
import org.codingmatters.poom.ci.dependency.api.repositorydownstreamrepositoriesgetresponse.Status400;
import org.codingmatters.poom.ci.dependency.api.repositorydownstreamrepositoriesgetresponse.Status404;
import org.codingmatters.poom.ci.dependency.api.repositorydownstreamrepositoriesgetresponse.Status500;
import org.codingmatters.poom.ci.dependency.api.types.Error;
import org.codingmatters.poom.ci.dependency.flat.DownstreamProcessor;
import org.codingmatters.poom.ci.dependency.flat.GraphManager;
import org.codingmatters.poom.ci.dependency.flat.GraphManagerException;
import org.codingmatters.poom.ci.dependency.flat.NoSuchRepositoryException;
import org.codingmatters.poom.services.logging.CategorizedLogger;

import java.util.function.Function;

public class RepositoryDownstreams implements Function<RepositoryDownstreamRepositoriesGetRequest, RepositoryDownstreamRepositoriesGetResponse> {
    static private final CategorizedLogger log = CategorizedLogger.getLogger(RepositoryDownstreams.class);

    private final GraphManager graphManager;

    public RepositoryDownstreams(GraphManager graphManager) {
        this.graphManager = graphManager;
    }

    @Override
    public RepositoryDownstreamRepositoriesGetResponse apply(RepositoryDownstreamRepositoriesGetRequest request) {
        if(! request.opt().repositoryId().isPresent()) {
            return RepositoryDownstreamRepositoriesGetResponse.builder().status400(Status400.builder().payload(Error.builder()
                    .code(Error.Code.ILLEGAL_REQUEST)
                    .token(log.tokenized().info("unexpected error while calculating downstream : {}", request))
                    .description("must provide a repository id")
                    .build()).build()).build();
        }
        try {
            return RepositoryDownstreamRepositoriesGetResponse.builder().status200(Status200.builder()
                    .payload(new DownstreamProcessor(this.graphManager).downstream(request.repositoryId()))
                    .build()).build();
        } catch (NoSuchRepositoryException e) {
            return RepositoryDownstreamRepositoriesGetResponse.builder().status404(Status404.builder().payload(Error.builder()
                    .code(Error.Code.RESOURCE_NOT_FOUND)
                    .token(log.tokenized().info("unexpected error while calculating downstream : " + request, e))
                    .description("repository not found")
                    .build()).build()).build();
        } catch (GraphManagerException e) {
            return RepositoryDownstreamRepositoriesGetResponse.builder().status500(Status500.builder().payload(Error.builder()
                    .code(Error.Code.UNEXPECTED_ERROR)
                    .token(log.tokenized().error("unexpected error while calculating downstream : " + request, e))
                    .description("unexpected error see logs")
                    .build()).build()).build();
        }
    }
}

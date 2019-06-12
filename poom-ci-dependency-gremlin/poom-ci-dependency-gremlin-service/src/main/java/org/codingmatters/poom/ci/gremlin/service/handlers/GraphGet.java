package org.codingmatters.poom.ci.gremlin.service.handlers;

import org.apache.tinkerpop.gremlin.driver.remote.DriverRemoteConnection;
import org.apache.tinkerpop.gremlin.process.traversal.AnonymousTraversalSource;
import org.apache.tinkerpop.gremlin.process.traversal.dsl.graph.GraphTraversal;
import org.apache.tinkerpop.gremlin.process.traversal.dsl.graph.GraphTraversalSource;
import org.apache.tinkerpop.gremlin.process.traversal.dsl.graph.__;
import org.apache.tinkerpop.gremlin.structure.Vertex;
import org.apache.tinkerpop.gremlin.structure.VertexProperty;
import org.codingmatters.poom.ci.dependency.api.RepositoryGraphGetRequest;
import org.codingmatters.poom.ci.dependency.api.RepositoryGraphGetResponse;
import org.codingmatters.poom.ci.dependency.api.repositorygraphgetresponse.Status200;
import org.codingmatters.poom.ci.dependency.api.types.Module;
import org.codingmatters.poom.ci.dependency.api.types.Repository;
import org.codingmatters.poom.ci.dependency.api.types.RepositoryGraph;
import org.codingmatters.poom.ci.dependency.api.types.RepositoryRelation;
import org.codingmatters.poom.ci.gremlin.queries.RepositoryQuery;
import org.codingmatters.poom.ci.gremlin.service.Mappers;
import org.codingmatters.value.objects.values.ObjectValue;

import java.util.Collection;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

public class GraphGet implements Function<RepositoryGraphGetRequest, RepositoryGraphGetResponse> {
    private final DriverRemoteConnection connection;

    public GraphGet(DriverRemoteConnection connection) {
        this.connection = connection;
    }

    @Override
    public RepositoryGraphGetResponse apply(RepositoryGraphGetRequest repositoryGraphGetRequest) {

        return RepositoryGraphGetResponse.builder()
                .status200(Status200.builder()
                        .payload(RepositoryGraph.builder()
                                .roots(this.roots(AnonymousTraversalSource.traversal().withRemote(this.connection)))
                                .repositories(new RepositoryQuery<>(AnonymousTraversalSource.traversal().withRemote(this.connection), Mappers::repository).all())
                                .relations(this.relations(AnonymousTraversalSource.traversal().withRemote(this.connection)))
                                .build())
                        .build())
                .build();
    }

    /**
     *
     * A is not a root if :
     * B -produces-> M <-depends-on- A
     *
     * @param graph
     * @return
     */
    private List<String> roots(GraphTraversalSource graph) {
        List<String> result = new LinkedList<>();
        GraphTraversal<Vertex, Map<String, Object>> query = graph.V().hasLabel("repository")
                .not(__.out("depends-on").hasLabel("module").in("produces").hasLabel("repository"))
                .propertyMap("repository-id")
                ;
        while(query.hasNext()) {
            List<VertexProperty> elemnt = (List<VertexProperty>) query.next().get("repository-id");
            result.add(! elemnt.isEmpty() ? (String) elemnt.get(0).value() : null);
        }
        return result;
    }

    private List<RepositoryRelation> relations(GraphTraversalSource graph) {
        List<RepositoryRelation> results = new LinkedList<>();

        GraphTraversal<Vertex, Map<String, Object>> query = graph.V().hasLabel("repository").as("upstream")
                .out("produces").hasLabel("module").as("dependency")
                .in("depends-on").hasLabel("repository").as("downstream")

                .select("upstream").propertyMap("repository-id").as("upstream")
                .select("dependency").propertyMap("spec", "version").as("dependency")
                .select("downstream").propertyMap("repository-id").as("downstream")

                .select("upstream", "dependency", "downstream")
                ;

        while (query.hasNext()) {
            Map<String, Object> tuple = query.next();

            String upstreamId = Mappers.singlePropertyValue((Map<String, List<VertexProperty>>) tuple.get("upstream"), "repository-id");
            String downstreamId = Mappers.singlePropertyValue((Map<String, List<VertexProperty>>) tuple.get("downstream"), "repository-id");
            Module dependency = Mappers.module((Map<String, List<VertexProperty>>) tuple.get("dependency"));

            results.add(RepositoryRelation.builder()
                    .upstreamRepository(upstreamId)
                    .downstreamRepository(downstreamId)
                    .dependency(dependency)
                    .build());
        }

        return results;
    }
}

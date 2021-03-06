package org.codingmatters.poom.ci.gremlin.queries;

import org.apache.tinkerpop.gremlin.process.traversal.dsl.graph.GraphTraversal;
import org.apache.tinkerpop.gremlin.process.traversal.dsl.graph.GraphTraversalSource;
import org.apache.tinkerpop.gremlin.structure.Vertex;
import org.apache.tinkerpop.gremlin.structure.VertexProperty;

import java.util.*;
import java.util.function.Function;

public class DownstreamQuery<T> extends VertexQuery<T> {
    public DownstreamQuery(GraphTraversalSource graph, Function<Map<String, List<VertexProperty>>, T> vertexMapper) {
        super(graph, vertexMapper, "repository-id", "name", "checkout-spec");
    }

    public List<T> forRepository(String repositoryId) {
        Vertex repo = this.graph().V().has("kind", "repository").has("repository-id", repositoryId).next();

        return new LinkedList<>(new HashSet<>(this.processTraversal(
                this.graph().V(repo.id())
                        .out("produces").has("kind", "module")
                        .in("depends-on").has("kind", "repository").as("downstream")
                        .select("downstream")
        )));
    }
}

package org.codingmatters.poom.ci.gremlin.service.handlers;

import org.apache.tinkerpop.gremlin.process.remote.RemoteConnection;
import org.apache.tinkerpop.gremlin.process.traversal.AnonymousTraversalSource;
import org.apache.tinkerpop.gremlin.process.traversal.dsl.graph.GraphTraversalSource;
import org.codingmatters.poom.ci.dependency.api.RepositoryPutRequest;
import org.codingmatters.poom.ci.dependency.api.RepositoryPutResponse;
import org.codingmatters.poom.ci.dependency.api.types.Error;
import org.codingmatters.poom.ci.dependency.api.types.FullRepository;
import org.codingmatters.poom.ci.dependency.api.types.Module;
import org.codingmatters.poom.ci.dependency.api.types.Repository;
import org.codingmatters.poom.ci.dependency.api.types.ValueList;
import org.codingmatters.poom.ci.gremlin.queries.*;
import org.codingmatters.poom.ci.gremlin.service.Mappers;
import org.codingmatters.poom.services.logging.CategorizedLogger;

import java.util.LinkedList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collectors;

public class CreateOrUpdateRepository implements Function<RepositoryPutRequest, RepositoryPutResponse> {
    static private final CategorizedLogger log = CategorizedLogger.getLogger(CreateOrUpdateRepository.class);

    private Supplier<RemoteConnection> connectionSupplier;

    public CreateOrUpdateRepository(Supplier<RemoteConnection> connectionSupplier) {
        this.connectionSupplier = connectionSupplier;
    }

    @Override
    public RepositoryPutResponse apply(RepositoryPutRequest request) {
        try(RemoteConnection connection = this.connectionSupplier.get()) {
            GraphTraversalSource g = AnonymousTraversalSource.traversal().withRemote(connection);

            FullRepository fullRepository = request.payload();
            Set<Module> deps = fullRepository.opt().dependencies().orElse(new ValueList.Builder<Module>().build()).stream().collect(Collectors.toSet());
            Set<Module> produced = fullRepository.opt().produces().orElse(new ValueList.Builder<Module>().build()).stream().collect(Collectors.toSet());
            deps.removeAll(produced);

            fullRepository = fullRepository.changed(builder -> builder.dependencies(deps).produces(produced));

            new CreateOrUpdateRepositoryQuery(g).update(request.repositoryId(), fullRepository.name(), fullRepository.checkoutSpec());
            new UpdateRepositoryProducedByQuery(g).update(request.repositoryId(), this.moduleSpecs(fullRepository.produces()));
            new UpdateRepositoryDependenciesQuery(g).update(request.repositoryId(), this.moduleSpecs(fullRepository.dependencies()));

            Optional<Repository> repository = new RepositoryQuery<>(AnonymousTraversalSource.traversal().withRemote(connection), Mappers::repository)
                    .repository(request.repositoryId());

            log.info("updated repository with : {}", fullRepository);
            return RepositoryPutResponse.builder().status200(status -> status.payload(repository.get())).build();
        } catch (Exception e) {
            return RepositoryPutResponse.builder()
                    .status500(status -> status.payload(Error.builder().code(Error.Code.UNEXPECTED_ERROR).token(log.tokenized().error("error accessing gremlin server", e)).build()))
                    .build();
        }
    }

    private Schema.ModuleSpec[] moduleSpecs(ValueList<Module> produces) {
        List<Schema.ModuleSpec> result = new LinkedList<>();
        produces.stream().forEach(module -> result.add(new Schema.ModuleSpec(module.spec(), module.version())));
        return result.toArray(new Schema.ModuleSpec[result.size()]);
    }
}

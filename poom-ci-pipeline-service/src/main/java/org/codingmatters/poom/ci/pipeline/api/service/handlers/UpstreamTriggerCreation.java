package org.codingmatters.poom.ci.pipeline.api.service.handlers;

import org.codingmatters.poom.ci.pipeline.api.UpstreamBuildTriggersPostRequest;
import org.codingmatters.poom.ci.pipeline.api.UpstreamBuildTriggersPostResponse;
import org.codingmatters.poom.ci.pipeline.api.service.repository.PoomCIRepository;
import org.codingmatters.poom.ci.pipeline.api.service.storage.UpstreamBuildQuery;
import org.codingmatters.poom.ci.pipeline.api.types.Error;
import org.codingmatters.poom.ci.pipeline.api.types.PipelineTrigger;
import org.codingmatters.poom.ci.triggers.UpstreamBuild;
import org.codingmatters.poom.services.domain.exceptions.RepositoryException;
import org.codingmatters.poom.services.domain.property.query.PropertyQuery;
import org.codingmatters.poom.services.domain.repositories.Repository;
import org.codingmatters.poom.services.logging.CategorizedLogger;
import org.codingmatters.poom.services.domain.entities.Entity;
import org.codingmatters.poom.services.domain.entities.PagedEntityList;
import org.codingmatters.rest.api.Processor;

import java.util.function.Consumer;
import java.util.function.Function;

public class UpstreamTriggerCreation implements Function<UpstreamBuildTriggersPostRequest, UpstreamBuildTriggersPostResponse> {
    static private final CategorizedLogger log = CategorizedLogger.getLogger(UpstreamTriggerCreation.class);

    private final Repository<UpstreamBuild, PropertyQuery> repository;
    private final Consumer<PipelineTrigger> triggerCreated;

    public UpstreamTriggerCreation(PoomCIRepository repository, Consumer<PipelineTrigger> triggerCreated) {
        this.repository = repository.upstreamBuildRepository();
        this.triggerCreated = triggerCreated;
    }

    @Override
    public UpstreamBuildTriggersPostResponse apply(UpstreamBuildTriggersPostRequest request) {
        try {
            PagedEntityList<UpstreamBuild> existing = this.repository.search(
                    PropertyQuery.builder()
                            .filter(String.format(
                                    "downstream.id == '%s' && consumed == false",
                                    request.payload().downstream().id()
                            ))
                            .build(),
                    0, 0);

            if(! existing.isEmpty()) {
                log.info("downstream already waiting for build, not triggering new one ({})", existing.get(0));
                return UpstreamBuildTriggersPostResponse.builder()
                        .status201(status -> status
                                .xEntityId(existing.get(0).id())
                                .location(Processor.Variables.API_PATH.token() + "/triggers/upstream-build/" + existing.get(0).id())
                        )
                        .build();
            } else {
                Entity<UpstreamBuild> trigger = this.repository.create(request.payload().withConsumed(false));
                log.audit().info("trigger created for upstream build {}", trigger);

                this.triggerCreated.accept(PipelineTrigger.builder()
                        .type(PipelineTrigger.Type.UPSTREAM_BUILD)
                        .triggerId(trigger.id())
                        .name(this.nameFrom(request.payload()))
                        .build());
                return UpstreamBuildTriggersPostResponse.builder()
                        .status201(status -> status
                                .xEntityId(trigger.id())
                                .location(Processor.Variables.API_PATH.token() + "/triggers/upstream-build/" + trigger.id())
                        )
                        .build();
            }
        } catch (RepositoryException e) {
            return UpstreamBuildTriggersPostResponse.builder().status500(status -> status.payload(error -> error
                    .token(log.tokenized().error("error while storing push event to repository", e))
                    .code(Error.Code.UNEXPECTED_ERROR)
            ))
                    .build();
        }
    }

    private String nameFrom(UpstreamBuild upstream) {
        return String.format(
                "%s (%s) triggered by upstream build : %s",
                upstream.downstream().name(),
                upstream.downstream().checkoutSpec(),
                upstream.upstream().name()
                );
    }
}

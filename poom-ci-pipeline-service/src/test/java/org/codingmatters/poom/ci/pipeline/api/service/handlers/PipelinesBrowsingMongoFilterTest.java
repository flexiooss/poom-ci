package org.codingmatters.poom.ci.pipeline.api.service.handlers;

import io.flexio.docker.DockerResource;
import io.flexio.services.tests.mongo.MongoResource;
import io.flexio.services.tests.mongo.MongoTest;
import org.codingmatters.poom.ci.pipeline.api.PipelinesGetRequest;
import org.codingmatters.poom.ci.pipeline.api.PipelinesGetResponse;
import org.codingmatters.poom.ci.pipeline.api.service.PoomCIPipelineService;
import org.codingmatters.poom.ci.pipeline.api.service.repository.PoomCIRepository;
import org.codingmatters.poom.ci.pipeline.api.service.repository.logs.RepositoryLogStore;
import org.codingmatters.poom.ci.pipeline.api.service.storage.PipelineStage;
import org.codingmatters.poom.ci.pipeline.api.service.storage.StageLog;
import org.codingmatters.poom.ci.pipeline.api.types.Pipeline;
import org.codingmatters.poom.ci.pipeline.api.types.PipelineTrigger;
import org.codingmatters.poom.ci.pipeline.api.types.pipeline.Status;
import org.codingmatters.poom.ci.triggers.GithubPushEvent;
import org.codingmatters.poom.ci.triggers.UpstreamBuild;
import org.codingmatters.poom.services.domain.repositories.inmemory.InMemoryRepositoryWithPropertyQuery;
import org.junit.After;
import org.junit.Before;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;

/**
 * Filtrage PropertyQuery du browsing des pipelines ({@code GET /pipelines?filter=…&order-by=…}) sur le
 * dépôt Mongo réellement utilisé en production ({@link PoomCIPipelineService#pipelineRepository}).
 *
 * Le Mongo est celui des tests Flexio (conteneur docker {@code mongo-ut}, cf. flexio-mongo-tests).
 */
public class PipelinesBrowsingMongoFilterTest {

    public static final String DB = "poom-ci-pipelines-filter-test";

    @ClassRule
    static public DockerResource docker = MongoTest.docker().started().finallyStarted();

    @Rule
    public MongoResource mongo = MongoTest.mongo(() -> docker).testDB(DB);

    private RepositoryLogStore logStore;
    private PoomCIRepository repository;
    private PipelinesBrowsing handler;

    @Before
    public void setUp() throws Exception {
        this.logStore = new RepositoryLogStore(InMemoryRepositoryWithPropertyQuery.validating(StageLog.class));
        this.repository = new PoomCIRepository(
                this.logStore,
                PoomCIPipelineService.pipelineRepository(this.mongo.newClient(), DB),
                InMemoryRepositoryWithPropertyQuery.validating(GithubPushEvent.class),
                InMemoryRepositoryWithPropertyQuery.validating(UpstreamBuild.class),
                InMemoryRepositoryWithPropertyQuery.validating(PipelineStage.class)
        );
        this.handler = new PipelinesBrowsing(this.repository);

        this.pipeline("flexio-commons", "flexiooss/flexio-commons", Status.Run.DONE, Status.Exit.SUCCESS, LocalDateTime.parse("2026-11-20T10:00:00"));
        this.pipeline("Flexio-Services", "flexio-corp/services", Status.Run.DONE, Status.Exit.FAILURE, LocalDateTime.parse("2026-11-24T10:00:00"));
        this.pipeline("MY-FLEXIO-APP", "acme/app", Status.Run.RUNNING, null, LocalDateTime.parse("2026-11-25T08:00:00"));
        this.pipeline("poom-ci", "flexiooss/poom-ci", Status.Run.PENDING, null, LocalDateTime.parse("2026-11-25T09:00:00"));
        this.pipeline("codingmatters-rest", "flexiooss/codingmatters-rest", Status.Run.DONE, Status.Exit.SUCCESS, LocalDateTime.parse("2026-11-26T09:00:00"));
    }

    @After
    public void tearDown() throws Exception {
        this.logStore.close();
    }

    @Test
    public void givenCaseInsensitivePattern__whenFilteringOnName__thenMatchesIgnoringCase() throws Exception {
        assertThat(this.names("name =~ /.*flexio.*/i", null),
                containsInAnyOrder("flexio-commons", "Flexio-Services", "MY-FLEXIO-APP"));
    }

    @Test
    public void givenCaseSensitivePattern__whenFilteringOnName__thenMatchesWithCase() throws Exception {
        assertThat(this.names("name =~ /.*flexio.*/", null),
                containsInAnyOrder("flexio-commons"));
    }

    @Test
    public void givenEuiQuickSearchForm__whenOrOnSeveralProperties__thenMatchesAnyOfThem() throws Exception {
        // forme générée par la QUICK_SEARCH standard des EUI Flexio
        assertThat(this.names("(name =~ /.*flexiooss.*/i) || (trigger.checkoutSpec =~ /.*FLEXIOOSS.*/i)", null),
                containsInAnyOrder("flexio-commons", "poom-ci", "codingmatters-rest"));
    }

    @Test
    public void givenPatternAndNestedEquality__whenAnd__thenBothApply() throws Exception {
        assertThat(this.names("name =~ /.*flexio.*/i && status.run == 'DONE'", null),
                containsInAnyOrder("flexio-commons", "Flexio-Services"));
        assertThat(this.names("(name =~ /.*flexio.*/i || name =~ /^poom/i) && status.run != 'DONE'", null),
                containsInAnyOrder("MY-FLEXIO-APP", "poom-ci"));
    }

    @Test
    public void givenNestedProperties__whenFiltering__thenFiltered() throws Exception {
        assertThat(this.names("status.run == 'RUNNING'", null), contains("MY-FLEXIO-APP"));
        assertThat(this.names("status.run in ('PENDING', 'RUNNING')", null), containsInAnyOrder("MY-FLEXIO-APP", "poom-ci"));
        assertThat(this.names("status.exit == 'FAILURE'", null), contains("Flexio-Services"));
        assertThat(this.names("status.exit == null", null), containsInAnyOrder("MY-FLEXIO-APP", "poom-ci"));
        assertThat(this.names("trigger.checkoutSpec =~ /^flexio-corp.services$/i", null), contains("Flexio-Services"));
    }

    @Test
    public void givenDateLiterals__whenComparing__thenFiltered() throws Exception {
        assertThat(this.names("status.triggered > 2026-11-25T00:00:00", null),
                containsInAnyOrder("MY-FLEXIO-APP", "poom-ci", "codingmatters-rest"));
        assertThat(this.names("status.triggered >= 2026-11-24T10:00:00 && status.triggered < 2026-11-25T08:30:00", null),
                containsInAnyOrder("Flexio-Services", "MY-FLEXIO-APP"));
        assertThat(this.names("name =~ /.*flexio.*/i && status.triggered > 2026-11-21T00:00:00.000", null),
                containsInAnyOrder("Flexio-Services", "MY-FLEXIO-APP"));
    }

    @Test
    public void givenZonedDateLiterals__whenComparingDatetimeOnlyProperty__thenNothingMatches() throws Exception {
        // les dates de pipeline sont des datetime-only (stockées en Date Mongo) ; un littéral zoné (`Z` ou `+01:00`)
        // est comparé par flexio-mongo-io à `<propriété>.ts` (stockage des datetime zonées) : aucun résultat, sans erreur.
        assertThat(this.names("status.triggered > 2026-11-21T00:00:00Z", null), is(empty()));
        assertThat(this.names("status.triggered > 2026-11-21T00:00:00.000+01:00", null), is(empty()));
    }

    @Test
    public void givenPatternContainingSlash__whenFiltering__thenSyntaxError() throws Exception {
        // la grammaire PropertyFilter (PATTERN : '/' ~('/')* '/' I?) n'accepte aucun `/` dans un motif, même échappé :
        // une recherche rapide sur une valeur contenant `/` (ex. un checkout spec) part en erreur 500.
        assertThat(this.browse("trigger.checkoutSpec =~ /.*flexiooss/poom.*/i", null).opt().status500().isPresent(), is(true));
        assertThat(this.browse("trigger.checkoutSpec =~ /.*flexiooss\\/poom.*/i", null).opt().status500().isPresent(), is(true));
        // contournement : `contains` (sensible à la casse) ou `.` à la place du `/`
        assertThat(this.names("trigger.checkoutSpec contains 'flexiooss/poom'", null), contains("poom-ci"));
        assertThat(this.names("trigger.checkoutSpec =~ /.*flexiooss.poom.*/i", null), contains("poom-ci"));
    }

    @Test
    public void givenOrderBy__whenBrowsing__thenSorted() throws Exception {
        assertThat(this.names(null, "status.triggered desc"),
                contains("codingmatters-rest", "poom-ci", "MY-FLEXIO-APP", "Flexio-Services", "flexio-commons"));
        assertThat(this.names("name =~ /.*flexio.*/i", "status.triggered asc"),
                contains("flexio-commons", "Flexio-Services", "MY-FLEXIO-APP"));
        assertThat(this.names("name =~ /.*flexio.*/i", "status.run asc, name desc"),
                contains("flexio-commons", "Flexio-Services", "MY-FLEXIO-APP"));
    }

    @Test
    public void givenJavaPropertyName__whenFilteringOnDashedRamlProperty__thenFiltered() throws Exception {
        // la propriété RAML `checkout-spec` est stockée dans Mongo sous son nom Java `checkoutSpec` :
        // c'est ce nom qu'il faut utiliser dans le filtre et le tri.
        assertThat(this.names("trigger.checkoutSpec == 'acme/app'", null), contains("MY-FLEXIO-APP"));
        // tri par checkout spec : acme/app, flexio-corp/services, flexiooss/codingmatters-rest, flexiooss/flexio-commons, flexiooss/poom-ci
        assertThat(this.names(null, "trigger.checkoutSpec asc"),
                contains("MY-FLEXIO-APP", "Flexio-Services", "codingmatters-rest", "flexio-commons", "poom-ci"));
    }

    @Test
    public void givenJsonPropertyName__whenFilteringOnDashedRamlProperty__thenNothingMatchesWithoutError() throws Exception {
        // le nom JSON `checkout-spec` n'existe pas dans Mongo : pas d'erreur, aucun résultat
        assertThat(this.names("trigger.checkout-spec == 'acme/app'", null), is(empty()));
        assertThat(this.names("trigger.checkout-spec =~ /.*acme.*/i", null), is(empty()));
    }

    @Test
    public void givenUnsupportedSyntax__whenFiltering__thenUnexpectedError() throws Exception {
        // `is null` n'existe pas dans la grammaire PropertyFilter de poom-services (seulement `== null`,
        // `is empty`, `is not empty`) : la requête est rejetée en 500
        assertThat(this.browse("status.exit is null", null).opt().status500().isPresent(), is(true));
    }

    @Test
    public void givenIsEmpty__whenFiltering__thenMatchesNullValues() throws Exception {
        assertThat(this.names("status.exit is empty", null), containsInAnyOrder("MY-FLEXIO-APP", "poom-ci"));
        assertThat(this.names("status.exit is not empty", null), containsInAnyOrder("flexio-commons", "Flexio-Services", "codingmatters-rest"));
    }

    private void pipeline(String name, String checkoutSpec, Status.Run run, Status.Exit exit, LocalDateTime triggered) throws Exception {
        this.repository.pipelineRepository().create(Pipeline.builder()
                .name(name)
                .status(Status.builder().run(run).exit(exit).triggered(triggered).build())
                .trigger(PipelineTrigger.builder().type(PipelineTrigger.Type.GITHUB_PUSH).checkoutSpec(checkoutSpec).build())
                .build());
    }

    private PipelinesGetResponse browse(String filter, String orderBy) {
        return this.handler.apply(PipelinesGetRequest.builder().filter(filter).orderBy(orderBy).build());
    }

    private List<String> names(String filter, String orderBy) {
        PipelinesGetResponse response = this.browse(filter, orderBy);
        Stream<Pipeline> pipelines;
        if (response.opt().status200().isPresent()) {
            pipelines = response.status200().payload().stream();
        } else if (response.opt().status206().isPresent()) {
            pipelines = response.status206().payload().stream();
        } else {
            throw new AssertionError("unexpected response for filter=" + filter + " order-by=" + orderBy + " : " + response);
        }
        return pipelines.map(Pipeline::name).collect(Collectors.toList());
    }
}

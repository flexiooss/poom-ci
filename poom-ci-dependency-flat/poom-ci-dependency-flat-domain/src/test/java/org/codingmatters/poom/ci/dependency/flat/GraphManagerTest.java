package org.codingmatters.poom.ci.dependency.flat;

import org.codingmatters.poom.ci.dependency.api.types.FullRepository;
import org.codingmatters.poom.ci.dependency.api.types.Module;
import org.codingmatters.poom.ci.dependency.flat.domain.spec.DependsOnRelation;
import org.codingmatters.poom.ci.dependency.flat.domain.spec.ProducesRelation;
import org.codingmatters.poom.services.domain.property.query.PropertyQuery;
import org.codingmatters.poom.services.domain.repositories.Repository;
import org.codingmatters.poom.services.domain.repositories.inmemory.InMemoryRepositoryWithPropertyQuery;
import org.junit.Assert;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;

import java.util.Optional;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;

public class GraphManagerTest {

    private static final org.codingmatters.poom.ci.dependency.api.types.Repository MY_REPO = org.codingmatters.poom.ci.dependency.api.types.Repository.builder()
            .id("my-repo-id")
            .name("my-repo")
            .checkoutSpec("my/repo/checkout/spec")
            .build();

    private static final Module MODULE_1 = Module.builder().spec("module-1").version("1.1").build();
    private static final Module MODULE_2 = Module.builder().spec("module-2").version("2.1").build();
    private static final int TEST_PAGE_SIZE = 10;


    @Rule
    public ExpectedException thrown = ExpectedException.none();

    private Repository<org.codingmatters.poom.ci.dependency.api.types.Repository, PropertyQuery> repositories = InMemoryRepositoryWithPropertyQuery.validating(org.codingmatters.poom.ci.dependency.api.types.Repository.class);
    private Repository<ProducesRelation, PropertyQuery> producesRelations = InMemoryRepositoryWithPropertyQuery.validating(ProducesRelation.class);
    private Repository<DependsOnRelation, PropertyQuery> dependsOnRelations = InMemoryRepositoryWithPropertyQuery.validating(DependsOnRelation.class);

    private GraphManager manager = new GraphManager(repositories, this.producesRelations, this.dependsOnRelations, TEST_PAGE_SIZE);


    @Test
    public void givenReposAreEmpty__whenIndexing_andNoDependencies_andNoProduction__thenRepoIsStored_andNoRelationsAreStored() throws Exception {
        this.manager.index(FullRepository.builder()
                .id(MY_REPO.id())
                .name(MY_REPO.name())
                .checkoutSpec(MY_REPO.checkoutSpec())
                .build());

        assertThat(this.repositories.all(0, 1000).valueList(), contains(MY_REPO));
        assertThat(this.producesRelations.all(0, 1000).valueList(), is(empty()));
        assertThat(this.dependsOnRelations.all(0, 1000).valueList(), is(empty()));
    }

    @Test
    public void givenReposAreEmpty__whenIndexing_andHasDependencies_andHasProductions__thenRepoIsStored_andRelationsAreStored() throws Exception {
        this.manager.index(FullRepository.builder()
                .id(MY_REPO.id())
                .name(MY_REPO.name())
                .checkoutSpec(MY_REPO.checkoutSpec())
                .dependencies(MODULE_1)
                .produces(MODULE_2)
                .build());

        assertThat(this.repositories.all(0, 1000).valueList(), contains(MY_REPO));
        assertThat(this.dependsOnRelations.all(0, 1000).valueList(), contains(DependsOnRelation.builder().repository(MY_REPO).module(MODULE_1).build()));
        assertThat(this.producesRelations.all(0, 1000).valueList(), contains(ProducesRelation.builder().repository(MY_REPO).module(MODULE_2).build()));
    }

    @Test
    public void givenReposAreEmpty__whenIndexing_andHasDuplicateDependencies_andHasDuplicateProductions__thenRepoIsStored_andRelationsAreStoredOnce() throws Exception {
        this.manager.index(FullRepository.builder()
                .id(MY_REPO.id())
                .name(MY_REPO.name())
                .checkoutSpec(MY_REPO.checkoutSpec())
                .dependencies(MODULE_1, MODULE_1)
                .produces(MODULE_2, MODULE_2)
                .build());

        assertThat(this.repositories.all(0, 1000).valueList(), contains(MY_REPO));
        System.out.println(this.dependsOnRelations.all(0, 1000).valueList());
        assertThat(this.dependsOnRelations.all(0, 1000).valueList(), contains(DependsOnRelation.builder().repository(MY_REPO).module(MODULE_1).build()));
        assertThat(this.producesRelations.all(0, 1000).valueList(), contains(ProducesRelation.builder().repository(MY_REPO).module(MODULE_2).build()));
    }

    @Test
    public void givenReposAreEmpty__whenIndexing_andADependencyIsAProduction__thenRepoIsStored_andProductionIsStore_andDependencyIsIgnored() throws Exception {
        this.manager.index(FullRepository.builder()
                .id(MY_REPO.id())
                .name(MY_REPO.name())
                .checkoutSpec(MY_REPO.checkoutSpec())
                .dependencies(MODULE_1)
                .produces(MODULE_1)
                .build());

        assertThat(this.repositories.all(0, 1000).valueList(), contains(MY_REPO));
        assertThat(this.dependsOnRelations.all(0, 1000).valueList(), is(empty()));
        assertThat(this.producesRelations.all(0, 1000).valueList(), contains(ProducesRelation.builder().repository(MY_REPO).module(MODULE_1).build()));
    }

    @Test
    public void givenRepoAlreadyIndex__whenIndexingWithDifferentName__thenNameIsUpdated() throws Exception {
        this.repositories.createWithId(MY_REPO.id(), MY_REPO);

        this.manager.index(FullRepository.builder()
                .id(MY_REPO.id())
                .name("changed")
                .checkoutSpec(MY_REPO.checkoutSpec())
                .build());

        assertThat(this.repositories.all(0, 1000).valueList(), contains(MY_REPO.withName("changed")));
    }

    @Test
    public void givenRepoAlreadyIndex__whenIndexingWithDifferentSpec__thenSpecIsUpdated() throws Exception {
        this.repositories.createWithId(MY_REPO.id(), MY_REPO);

        this.manager.index(FullRepository.builder()
                .id(MY_REPO.id())
                .name(MY_REPO.name())
                .checkoutSpec("changed/spec")
                .build());

        assertThat(this.repositories.all(0, 1000).valueList(), contains(MY_REPO.withCheckoutSpec("changed/spec")));
    }

    @Test
    public void givenRepoAlreadyIndexed_andRepoHadRelations__whenIndexingWithNoRelations__thenRelationsAreWiped() throws Exception {
        this.repositories.createWithId(MY_REPO.id(), MY_REPO);
        this.dependsOnRelations.create(DependsOnRelation.builder().repository(MY_REPO).module(MODULE_1).build());
        this.producesRelations.create(ProducesRelation.builder().repository(MY_REPO).module(MODULE_2).build());

        this.manager.index(FullRepository.builder()
                .id(MY_REPO.id())
                .name(MY_REPO.name())
                .checkoutSpec(MY_REPO.checkoutSpec())
                .build());

        assertThat(this.repositories.all(0, 1000).valueList(), contains(MY_REPO));
        assertThat(this.producesRelations.all(0, 1000).valueList(), is(empty()));
        assertThat(this.dependsOnRelations.all(0, 1000).valueList(), is(empty()));
    }


    @Test
    public void givenARepoAlreadyIndex__whenIndexingARepoWithDifferenId__thenBothReposAreIndexed() throws Exception {
        this.repositories.createWithId(MY_REPO.id(), MY_REPO);

        this.manager.index(FullRepository.builder()
                .id("another-id")
                .name(MY_REPO.name())
                .checkoutSpec(MY_REPO.checkoutSpec())
                .build());

        assertThat(this.repositories.all(0, 1000).valueList(), contains(
                MY_REPO,
                MY_REPO.withId("another-id")
        ));
    }

    @Test
    public void givenARepoAlreadyIndex_andRepoHasRelations__whenIndexingARepoWithDifferentId__thenBothReposAreIndexedAndRelationsAreKept() throws Exception {
        this.repositories.createWithId(MY_REPO.id(), MY_REPO);
        this.dependsOnRelations.create(DependsOnRelation.builder().repository(MY_REPO).module(MODULE_1).build());
        this.producesRelations.create(ProducesRelation.builder().repository(MY_REPO).module(MODULE_2).build());

        this.manager.index(FullRepository.builder()
                .id("another-id")
                .name(MY_REPO.name())
                .checkoutSpec(MY_REPO.checkoutSpec())
                .build());

        assertThat(this.repositories.all(0, 1000).valueList(), contains(
                MY_REPO,
                MY_REPO.withId("another-id")
        ));
        assertThat(this.dependsOnRelations.all(0, 1000).valueList(), contains(DependsOnRelation.builder().repository(MY_REPO).module(MODULE_1).build()));
        assertThat(this.producesRelations.all(0, 1000).valueList(), contains(ProducesRelation.builder().repository(MY_REPO).module(MODULE_2).build()));
    }


    @Test
    public void givenRepoNotIndexed__whenGettingProductions__thenNoSuchRepositoryException() throws Exception {
        thrown.expect(GraphManagerException.class);
        thrown.expectMessage("no such repository");

        this.manager.producedBy(MY_REPO);
    }

    @Test
    public void givenRepoNotIndexed__whenGettingDependencies__thenNoSuchRepositoryException() throws Exception {
        thrown.expect(GraphManagerException.class);
        thrown.expectMessage("no such repository");

        this.manager.dependenciesOf(MY_REPO);
    }

    @Test
    public void givenRepoIndexed_andRepoHasOneProduction__whenGettingProducedBy__thenProductionReturned() throws Exception {
        this.repositories.createWithId(MY_REPO.id(), MY_REPO);
        this.dependsOnRelations.create(DependsOnRelation.builder().repository(MY_REPO).module(MODULE_1).build());
        this.producesRelations.create(ProducesRelation.builder().repository(MY_REPO).module(MODULE_2).build());

        assertThat(this.manager.producedBy(MY_REPO), is(arrayContaining(MODULE_2)));
    }

    @Test
    public void givenRepoIndexed_andRepoHasOneDependency__whenGettingDependencies__thenDependencyReturned() throws Exception {
        this.repositories.createWithId(MY_REPO.id(), MY_REPO);
        this.dependsOnRelations.create(DependsOnRelation.builder().repository(MY_REPO).module(MODULE_1).build());
        this.producesRelations.create(ProducesRelation.builder().repository(MY_REPO).module(MODULE_2).build());

        assertThat(this.manager.dependenciesOf(MY_REPO), is(arrayContaining(MODULE_1)));
    }

    @Test
    public void paging() throws Exception {
        int count = 5 * TEST_PAGE_SIZE + TEST_PAGE_SIZE / 3;

        this.repositories.createWithId(MY_REPO.id(), MY_REPO);
        for (int i = 0; i < count; i++) {
            this.dependsOnRelations.create(DependsOnRelation.builder().repository(MY_REPO).module(MODULE_1.withSpec("module-dep-" + i)).build());
            this.producesRelations.create(ProducesRelation.builder().repository(MY_REPO).module(MODULE_2.withSpec("module-prod-" + i)).build());
        }

        assertThat(this.manager.dependenciesOf(MY_REPO), arrayWithSize(count));
        assertThat(this.manager.producedBy(MY_REPO), arrayWithSize(count));
    }


    @Test
    public void givenReposEmpty__whenListingRepositories__thenEmptyArray() throws Exception {
        assertThat(this.manager.repositories(), is(emptyArray()));
    }

    @Test
    public void givenSomeRepositoriesDefined__whenListingRepositories__thenReposAreListed_andOrderedOnName() throws Exception {
        this.repositories.createWithId("c", MY_REPO.withName("C"));
        this.repositories.createWithId("d", MY_REPO.withName("A"));
        this.repositories.createWithId("b", MY_REPO.withName("B"));

        org.codingmatters.poom.ci.dependency.api.types.Repository[] repositories = this.manager.repositories();
        System.out.println(repositories);
        assertThat(repositories, is(arrayContaining(
                MY_REPO.withName("A"),
                MY_REPO.withName("B"),
                MY_REPO.withName("C")
        )));
    }

    @Test
    public void givenRepositoryDoesntExist__whenGettingRepo__thenNoRepoReturned() throws Exception {
        assertThat(this.manager.repository(MY_REPO.id()).isPresent(), is(false));
    }

    @Test
    public void givenRepositoryExists__whenGettingFromId__thenRepositoryReturned() throws Exception {
        this.repositories.createWithId(MY_REPO.id(), MY_REPO);

        assertThat(this.manager.repository(MY_REPO.id()).get(), is(MY_REPO));
    }

    @Test
    public void givenRepositoryExists__whenDeleting__thenRepositoryDeleted() throws Exception {
        this.repositories.createWithId(MY_REPO.id(), MY_REPO);
        this.dependsOnRelations.create(DependsOnRelation.builder().repository(MY_REPO).module(MODULE_1).build());
        this.producesRelations.create(ProducesRelation.builder().repository(MY_REPO).module(MODULE_2).build());

        this.manager.deleteRepository(MY_REPO.id());

        assertThat(this.repositories.all(0L, 0L).total(), is(0L));
        assertThat(this.dependsOnRelations.all(0L, 0L).total(), is(0L));
        assertThat(this.producesRelations.all(0L, 0L).total(), is(0L));
    }


    @Test
    public void givenRepositoryExists__whenModuleNotProducedByAnyRepository__thenProductionRepositoriesIsEmpty() throws Exception {
        this.repositories.createWithId(MY_REPO.id(), MY_REPO);
        this.producesRelations.create(ProducesRelation.builder().repository(MY_REPO).module(MODULE_2).build());

        assertThat(this.manager.productionRepositories(Module.builder().spec("another-module").version("1").build()), is(emptyArray()));
    }

    @Test
    public void givenRepositoryExists__whenModuleNotProducedInThisVersion__thenProductionRepositoriesIsEmpty() throws Exception {
        this.repositories.createWithId(MY_REPO.id(), MY_REPO);
        this.producesRelations.create(ProducesRelation.builder().repository(MY_REPO).module(MODULE_2).build());

        assertThat(this.manager.productionRepositories(MODULE_2.withVersion("12")), is(emptyArray()));
    }

    @Test
    public void givenRepositoryExists__whenModulePrucedByRepository__thenProductionRepositoriesContainsThisRepository() throws Exception {
        this.repositories.createWithId(MY_REPO.id(), MY_REPO);
        this.producesRelations.create(ProducesRelation.builder().repository(MY_REPO).module(MODULE_2).build());

        assertThat(this.manager.productionRepositories(MODULE_2), is(arrayContaining(MY_REPO)));
    }





    @Test
    public void givenRepositoryExists__whenModuleNotDependencyOfAnyRepository__thenDependentRepositoriesIsEmpty() throws Exception {
        this.repositories.createWithId(MY_REPO.id(), MY_REPO);
        this.dependsOnRelations.create(DependsOnRelation.builder().repository(MY_REPO).module(MODULE_2).build());

        assertThat(this.manager.dependentRepositories(Module.builder().spec("another-module").version("1").build()), is(emptyArray()));
    }

    @Test
    public void givenRepositoryExists__whenModuleNotDependencyOfInThisVersion__thenDependentRepositoriesIsEmpty() throws Exception {
        this.repositories.createWithId(MY_REPO.id(), MY_REPO);
        this.dependsOnRelations.create(DependsOnRelation.builder().repository(MY_REPO).module(MODULE_2).build());

        assertThat(this.manager.dependentRepositories(MODULE_2.withVersion("12")), is(emptyArray()));
    }

    @Test
    public void givenRepositoryExists__whenModuleDependencyOfRepository__thenDependentRepositoriesContainsThisRepository() throws Exception {
        this.repositories.createWithId(MY_REPO.id(), MY_REPO);
        this.dependsOnRelations.create(DependsOnRelation.builder().repository(MY_REPO).module(MODULE_2).build());

        assertThat(this.manager.dependentRepositories(MODULE_2), is(arrayContaining(MY_REPO)));
    }
}
package org.codingmatters.poom.ci.apps.releaser;

import com.fasterxml.jackson.core.JsonFactory;
import org.codingmatters.poom.ci.apps.releaser.command.CommandHelper;
import org.codingmatters.poom.ci.apps.releaser.graph.GraphWalkResult;
import org.codingmatters.poom.ci.apps.releaser.graph.GraphWalker;
import org.codingmatters.poom.ci.apps.releaser.graph.PropagationContext;
import org.codingmatters.poom.ci.apps.releaser.graph.descriptors.RepositoryGraphDescriptor;
import org.codingmatters.poom.ci.apps.releaser.task.PropagateVersionsTask;
import org.codingmatters.poom.ci.apps.releaser.task.ReleaseTask;
import org.codingmatters.poom.ci.apps.releaser.task.ReleaseTaskResult;
import org.codingmatters.poom.ci.pipeline.client.PoomCIPipelineAPIClient;
import org.codingmatters.poom.ci.pipeline.client.PoomCIPipelineAPIRequesterClient;
import org.codingmatters.poom.services.logging.CategorizedLogger;
import org.codingmatters.poom.services.support.Arguments;
import org.codingmatters.poom.services.support.Env;
import org.codingmatters.rest.api.client.okhttp.OkHttpClientWrapper;
import org.codingmatters.rest.api.client.okhttp.OkHttpRequesterFactory;
import org.jetbrains.annotations.NotNull;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintStream;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;

public class App {
    static private final CategorizedLogger log = CategorizedLogger.getLogger(App.class);

    /**
     * RELEASE GRAPH
     * mvn exec:java -Dexec.mainClass=org.codingmatters.poom.ci.apps.releaser.App -Dexec.args="release-graph /tmp/playground/graph.yml"
     *
     * PROPAGATE VERSIONS
     * mvn exec:java -Dexec.mainClass=org.codingmatters.poom.ci.apps.releaser.App -Dexec.args="propagate-versions /tmp/playground/graph.yml"
     * @param args
     */
    public static void main(String[] args) {
        Arguments arguments = Arguments.from(args);

        if(arguments.argumentCount() < 1) {
            usageAndFail(args);
        }

        if(arguments.arguments().get(0).equals("help")) {
            usage(System.out, args);
            System.exit(0);
        }

        CommandHelper commandHelper = new CommandHelper(line -> System.out.println(line), line -> System.err.println(line));

        JsonFactory jsonFactory = new JsonFactory();
        String pipelineUrl = Env.optional("PIPELINES_URL")
                .orElseGet(() -> new Env.Var("https://pipelines.ci.flexio.io/pipelines")).asString() ;

        PoomCIPipelineAPIClient client = new PoomCIPipelineAPIRequesterClient(
                new OkHttpRequesterFactory(OkHttpClientWrapper.build(), () -> pipelineUrl),
                jsonFactory,
                pipelineUrl
        );


        if(arguments.arguments().get(0).equals("release")) {
            Arguments.OptionValue repository = arguments.option("repository");
            if(! repository.isPresent()) {
                usageAndFail(args);
            }
            if(repository.get() == null) {
                usageAndFail(args);
            }

            try {
                ReleaseTaskResult result = new ReleaseTask(repository.get(), commandHelper, client).call();
                if(result.exitStatus().equals(ReleaseTaskResult.ExitStatus.SUCCESS)) {
                    System.out.println(result.message());
                    System.exit(0);
                } else {
                    System.err.println(result.message());
                    System.exit(2);
                }
            } catch (Exception e) {
                log.error("failed executing release", e);
                System.exit(3);
            }
        } else if(arguments.arguments().get(0).equals("release-graph")) {
            if(arguments.argumentCount() < 1) {
                usageAndFail(args);
            }
            try {
                List<RepositoryGraphDescriptor> descriptorList = buildFilteredGraphDescriptorList(arguments);
                System.out.println("Will release dependency graphs : " + descriptorList);

                ExecutorService pool = Executors.newFixedThreadPool(10);
                GraphWalker.WalkerTaskProvider walkerTaskProvider = (repository, context) -> new ReleaseTask(repository, context, commandHelper, client);

                PropagationContext propagationContext = new PropagationContext();
                for (RepositoryGraphDescriptor descriptor : descriptorList) {
                    walkGraph(descriptor, propagationContext, pool, walkerTaskProvider);
                }

                System.out.println("\n\n\n\n####################################################################################");
                System.out.println("####################################################################################");
                System.out.printf("Finished releaseing graphs, released versions are : :\n");
                System.out.println(propagationContext.text());
                System.out.println("####################################################################################");
                System.out.println("####################################################################################\n\n");

                System.exit(0);
            } catch (Exception e) {
                log.error("failed executing release-graph", e);
                System.exit(3);
            }
        } else if(arguments.arguments().get(0).equals("propagate-versions")) {
            if(arguments.argumentCount() < 1) {
                usageAndFail(args);
            }
            try {
                List<RepositoryGraphDescriptor> descriptorList = buildFilteredGraphDescriptorList(arguments);
                System.out.println("Will propagate develop version for dependency graph : " + descriptorList);
                ExecutorService pool = Executors.newFixedThreadPool(10);

                GraphWalker.WalkerTaskProvider walkerTaskProvider = (repository, context) -> {
                    String branch = "develop";
                    if(arguments.option("branch").isPresent()) {
                        branch = arguments.option("branch").get();
                    }
                    return new PropagateVersionsTask(repository, branch, context, commandHelper, client);
                };

                PropagationContext propagationContext = new PropagationContext();
                for (RepositoryGraphDescriptor descriptor : descriptorList) {
                    walkGraph(descriptor, propagationContext, pool, walkerTaskProvider);
                }
                System.exit(0);
            } catch (Exception e) {
                log.error("failed executing release-graph", e);
                System.exit(3);
            }
        } else {
            usageAndFail(args);
        }


    }

    @NotNull
    private static List<RepositoryGraphDescriptor> buildFilteredGraphDescriptorList(Arguments arguments) throws IOException {
        List<RepositoryGraphDescriptor> descriptorList = new LinkedList<>();
        boolean searchStartFrom = arguments.option("from").isPresent();
        for (int i = 1; i < arguments.argumentCount(); i++) {
            String graphFilePath = arguments.arguments().get(i);
            RepositoryGraphDescriptor descriptor;
            try (InputStream in = new FileInputStream(graphFilePath)) {
                descriptor = RepositoryGraphDescriptor.fromYaml(in);
                if(searchStartFrom) {
                    if (descriptor.containsRepository(arguments.option("from").get())) {
                        descriptor = descriptor.subgraph(arguments.option("from").get());
                        descriptorList.add(descriptor);
                        searchStartFrom = false;
                    } else {
                        continue;
                    }
                } else {
                    descriptorList.add(descriptor);
                }
            }
        }
        return descriptorList;
    }

    private static void walkGraph(RepositoryGraphDescriptor descriptor, PropagationContext propagationContext, ExecutorService pool, GraphWalker.WalkerTaskProvider walkerTaskProvider) throws InterruptedException, java.util.concurrent.ExecutionException {
        GraphWalker releaseWalker = new GraphWalker(
                descriptor,
                propagationContext,
                walkerTaskProvider,
                pool
        );
        GraphWalkResult result = pool.submit(releaseWalker).get();
        if(result.exitStatus().equals(ReleaseTaskResult.ExitStatus.SUCCESS)) {
            System.out.println(result.message());
        } else {
            System.err.println(result.message());
            System.exit(2);
        }
    }

    private static void usageAndFail(String[] args) {
        usage(System.err, args);
        System.exit(1);
    }

    private static void usage(PrintStream where, String[] args) {
        where.println("Called with : " + args == null ? "" : Arrays.stream(args).collect(Collectors.joining(" ")));
        where.println("   help");
        where.println("      prints this usage message");
        where.println("   release --repository <repository, i.e. flexiooss/poom-ci>");
        where.println("      releases the repository and waits for the build pipeline to finish");
        where.println("   release-graph {--from <repo name>} <graph files>");
        where.println("      releases repository graphs");
        where.println("      --from   : using the from option, one can start releasing from one point in the graph");
        where.println("   propagate-versions {--from <repo name>} {--branch <branch name, defaults to develop>} <graph files>");
        where.println("      propagate versions in the repository graphs (version from preceding repos are propagated to following)");
        where.println("      --from   : using the from option, one can start propagating from one point in the graph");
        where.println("      --branch : by default, repos develop branch are used, one can change the branch using this option");
    }
}

package org.codingmatters.poom.ci.apps.releaser.graph;

import org.codingmatters.poom.ci.apps.releaser.graph.descriptors.RepositoryGraph;
import org.codingmatters.poom.ci.apps.releaser.graph.descriptors.RepositoryGraphDescriptor;
import org.codingmatters.poom.ci.apps.releaser.task.ReleaseTaskResult;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.*;

public class GraphWalker implements Callable<GraphWalkResult> {
    public interface WalkerTaskProvider {
        Callable<ReleaseTaskResult> create(String repository, PropagationContext context);
    }

    private final RepositoryGraphDescriptor repositoryGraph;
    private final PropagationContext propagationContext;
    private final WalkerTaskProvider walkerTaskProvider;
    private final ExecutorService pool;

    public GraphWalker(RepositoryGraphDescriptor repositoryGraph, PropagationContext propagationContext, WalkerTaskProvider walkerTaskProvider, ExecutorService pool) {
        this.repositoryGraph = repositoryGraph;
        this.propagationContext = propagationContext;
        this.walkerTaskProvider = walkerTaskProvider;
        this.pool = pool;
    }

    @Override
    public GraphWalkResult call() throws Exception {
        RepositoryGraph root = this.repositoryGraph.graph();
        Optional<GraphWalkResult> result = this.walk(root);
        return result.orElseGet(() -> new GraphWalkResult(ReleaseTaskResult.ExitStatus.SUCCESS, "graph processed"));
    }

    private Optional<GraphWalkResult> walk(RepositoryGraph root) throws ExecutionException, InterruptedException {
        if(root.opt().repositories().isPresent()) {
            for (String repository : root.repositories()) {
                ReleaseTaskResult result = this.pool.submit(this.walkerTaskProvider.create(repository, this.propagationContext)).get();
                if (result.exitStatus().equals(ReleaseTaskResult.ExitStatus.SUCCESS)) {
                    this.propagationContext.addPropagatedArtifact(result.releasedVersion());
                } else {
                    return Optional.of(new GraphWalkResult(ReleaseTaskResult.ExitStatus.FAILURE, result.message()));
                }
            }
        }

        List<Future<GraphWalkResult>> subtasks = new LinkedList<>();
        if(root.opt().then().isPresent()) {
            List<GraphWalker> walkers = new LinkedList<>();
            for (RepositoryGraph graph : root.then()) {

                GraphWalker walker = new GraphWalker(new RepositoryGraphDescriptor(graph), this.propagationContext, this.walkerTaskProvider, this.pool);
//                walkers.add(walker);
                Future<GraphWalkResult> task = this.pool.submit(walker);
                subtasks.add(task);
                System.out.printf("submitted %s\n", task);
            }
        }
        System.out.printf("graph has %s subtasks\n", subtasks.size());
        this.waitFor(subtasks);
        System.out.printf("graph %s subtasks done.\n", subtasks.size());

        boolean failures = false;
        StringBuilder failureMessages = new StringBuilder();
        int i = 0;
        for (Future<GraphWalkResult> subtask : subtasks) {
            GraphWalkResult result = subtask.get();
            if(! result.exitStatus().equals(ReleaseTaskResult.ExitStatus.SUCCESS)) {
                failures = true;
                failureMessages.append("- ").append(result.message()).append("\n");
            }
        }
        if(failures) {
            return Optional.of(new GraphWalkResult(ReleaseTaskResult.ExitStatus.FAILURE, failureMessages.toString()));
        } else {
            return Optional.empty();
        }
    }

    private void waitFor(List<Future<GraphWalkResult>> subtasks) {
        List<Future<GraphWalkResult>> remaining = new LinkedList<>(subtasks);
        while(! remaining.isEmpty()) {
            List<Future<GraphWalkResult>> done = new LinkedList<>();
            for (Future<GraphWalkResult> future : remaining) {
                try {
                    future.get(1000, TimeUnit.MILLISECONDS);
                    done.add(future);
                } catch (InterruptedException | ExecutionException e) {
                    throw new RuntimeException(e);
                } catch (TimeoutException e) {
                    System.out.printf("task not yet terminated...\n");
                }
            }
            remaining.removeAll(done);
            System.out.printf("%s subtasks just terminated, %s still running\n", done.size(), remaining.size());
            System.out.printf("\t%s\n", subtasks);
            if(! remaining.isEmpty()) {
                try {
                    Thread.sleep(1000);
                } catch (InterruptedException e) {
                    throw new RuntimeException(e);
                }
            }
        }
    }
}

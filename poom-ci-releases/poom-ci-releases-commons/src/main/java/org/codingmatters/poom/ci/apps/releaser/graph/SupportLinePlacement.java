package org.codingmatters.poom.ci.apps.releaser.graph;

import org.codingmatters.poom.ci.apps.releaser.graph.descriptors.RepositoryGraph;
import org.codingmatters.poom.ci.apps.releaser.graph.descriptors.RepositoryGraphDescriptor;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public class SupportLinePlacement {

    static public void check(TagVersions tagVersions, List<RepositoryGraphDescriptor> descriptorList) {
        if(tagVersions.isEmpty()) {
            return;
        }

        for (String repository : tagVersions.repositories()) {
            Set<String> successors = new LinkedHashSet<>();
            boolean found = false;
            for (RepositoryGraphDescriptor descriptor : descriptorList) {
                if(collectSuccessors(descriptor.graph(), repository, successors)) {
                    found = true;
                }
            }

            if(! found) {
                System.out.printf(
                        "%s is listed in --from-tag-version but appears in no graph being released : it will not be touched. Check its name, or ignore this if --from trimmed it out.%n",
                        repository);
            }
            if(! successors.isEmpty()) {
                throw new IllegalArgumentException(String.format(
                        "%s is listed in --from-tag-version but is not terminal in the graph : %s would receive its support version. Release it from master, or take it out of the graph.",
                        repository, String.join(", ", successors)));
            }
        }
    }

    static private boolean collectSuccessors(RepositoryGraph graph, String repository, Set<String> successors) {
        boolean found = false;
        boolean inThisList = false;

        if(graph.opt().repositories().isPresent()) {
            for (String current : graph.repositories()) {
                if(inThisList) {
                    successors.add(current);
                } else if(repository.equals(current)) {
                    inThisList = true;
                    found = true;
                }
            }
        }

        if(inThisList && graph.opt().then().isPresent()) {
            for (RepositoryGraph sub : graph.then()) {
                appendAll(sub, successors);
            }
        }

        if(graph.opt().then().isPresent()) {
            for (RepositoryGraph sub : graph.then()) {
                if(collectSuccessors(sub, repository, successors)) {
                    found = true;
                }
            }
        }

        return found;
    }

    static private void appendAll(RepositoryGraph graph, Set<String> successors) {
        if(graph.opt().repositories().isPresent()) {
            for (String current : graph.repositories()) {
                successors.add(current);
            }
        }
        if(graph.opt().then().isPresent()) {
            for (RepositoryGraph sub : graph.then()) {
                appendAll(sub, successors);
            }
        }
    }
}

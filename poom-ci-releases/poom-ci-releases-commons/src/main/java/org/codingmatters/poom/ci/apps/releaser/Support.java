package org.codingmatters.poom.ci.apps.releaser;

import org.codingmatters.poom.ci.apps.releaser.command.CommandHelper;
import org.codingmatters.poom.ci.apps.releaser.command.exception.CommandFailed;
import org.codingmatters.poom.ci.apps.releaser.flow.FlexioFlow;
import org.codingmatters.poom.ci.apps.releaser.git.Git;
import org.codingmatters.poom.ci.apps.releaser.git.GitRepository;
import org.codingmatters.poom.ci.apps.releaser.graph.PropagationContext;
import org.codingmatters.poom.ci.apps.releaser.hb.JsPackage;
import org.codingmatters.poom.ci.apps.releaser.maven.Pom;
import org.codingmatters.poom.ci.apps.releaser.maven.pom.ArtifactCoordinates;

import java.io.*;
import java.util.UUID;

public class Support {
    private final String repositoryUrl;
    private final String tag;
    private final PropagationContext propagationContext;
    private final CommandHelper commandHelper;
    private final Workspace workspace;

    public Support(String repositoryUrl, String tag, PropagationContext propagationContext, CommandHelper commandHelper, Workspace workspace) {
        this.repositoryUrl = repositoryUrl;
        this.tag = tag;
        this.propagationContext = propagationContext;
        this.commandHelper = commandHelper;
        this.workspace = workspace;
    }

    public SupportResult initiate() throws CommandFailed {
        File repoDir = workspace.mkdir(UUID.randomUUID().toString());
        repoDir.mkdir();

        Git git = new Git(repoDir, this.commandHelper);
        GitRepository repository = git.clone(this.repositoryUrl);
        FlexioFlow flow = new FlexioFlow(repoDir, this.commandHelper);
        repository.checkout("develop");

        System.out.println("\n\n\n\n####################################################################################");
        System.out.printf("Starting support of %s from tag %s with context :\n", this.repositoryUrl, this.tag);
        System.out.println(this.propagationContext.text());
        System.out.println("####################################################################################\n\n");

        flow.startSupportBranch(this.tag);

        ArtifactCoordinates coordinates = this.readProjectDescriptor(repoDir).project();
        String supportVersion = flow.version();
        String supportBranch = git.branch();

        if(! this.propagationContext.iEmpty()) {
            try {
                ProjectDescriptor currentPom = this.readProjectDescriptor(repoDir);
                ProjectDescriptor upgradedPom = this.propagationContext.applyTo(currentPom);
                if(upgradedPom.changedFrom(currentPom)) {
                    System.out.println("\n\n####################################################################################");
                    System.out.println("Versions propagated, need to write and commit");
                    System.out.println("####################################################################################\n\n");
                    this.writeProjectDescriptor(repoDir, upgradedPom);
                    repository.commit("propagating versions : \n" + this.propagationContext.text());
                }
            } catch (IOException e) {
                throw new CommandFailed("failed propagating versions", e);
            }
        }

        System.out.println("\n\n####################################################################################");
        System.out.println("Ready for support...");
        System.out.println("####################################################################################\n\n");

        flow.finishSupportBranch();

        ArtifactCoordinates result = new ArtifactCoordinates(coordinates.getGroupId(), coordinates.getArtifactId(), supportVersion);

        System.out.println("\n\n####################################################################################");
        System.out.printf("%s supported from tag %s.\n", result.coodinates(), this.tag);
        System.out.println("####################################################################################\n\n\n\n");

        return new SupportResult(result, supportBranch);
    }

    private ProjectDescriptor readProjectDescriptor(File workspace) throws CommandFailed {
        if(new File(workspace, "pom.xml").exists()) {
            try (InputStream pjDescFile = new FileInputStream(new File(workspace, "pom.xml"))) {
                return Pom.from(pjDescFile);
            } catch (IOException e) {
                throw new CommandFailed("failed reading pom for " + this.repositoryUrl, e);
            }
        } else if(new File(workspace, "package.json").exists()) {
            try (InputStream pjDescFile = new FileInputStream(new File(workspace, "package.json"))) {
                return JsPackage.read(pjDescFile);
            } catch (IOException e) {
                throw new CommandFailed("failed reading pom for " + this.repositoryUrl, e);
            }
        } else {
            return null;
        }
    }

    private void writeProjectDescriptor(File workspace, ProjectDescriptor projectDescriptor) throws CommandFailed {
        try(Writer writer = new FileWriter(new File(workspace, projectDescriptor.defaultFilename()))) {
            projectDescriptor.writeTo(writer);
        } catch (IOException e) {
            throw new CommandFailed("failed writing pom", e);
        }
    }
}

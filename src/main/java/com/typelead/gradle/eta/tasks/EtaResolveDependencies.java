package com.typelead.gradle.eta.tasks;

import java.io.File;
import java.util.Set;
import java.util.LinkedHashSet;
import java.util.stream.Stream;
import java.util.stream.Collectors;

import javax.inject.Inject;

import org.gradle.api.DefaultTask;
import org.gradle.api.GradleException;
import org.gradle.api.Project;
import org.gradle.api.file.RegularFile;
import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.file.ProjectLayout;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.OutputFile;
import org.gradle.api.tasks.TaskAction;
import org.gradle.api.tasks.TaskAction;
import org.gradle.api.provider.Provider;
import org.gradle.api.provider.ProviderFactory;
import org.gradle.api.artifacts.Configuration;

import com.typelead.gradle.utils.EtlasCommand;
import com.typelead.gradle.utils.ExtensionHelper;
import com.typelead.gradle.utils.CabalHelper;
import com.typelead.gradle.eta.api.EtaDependency;
import com.typelead.gradle.eta.api.EtaDirectDependency;
import com.typelead.gradle.eta.api.EtaGitDependency;
import com.typelead.gradle.eta.api.EtaConfiguration;
import com.typelead.gradle.eta.api.SourceRepository;
import com.typelead.gradle.eta.api.EtaExtension;
import com.typelead.gradle.eta.internal.DependencyUtils;
import com.typelead.gradle.eta.plugins.EtaBasePlugin;

public class EtaResolveDependencies extends DefaultTask {

    public static final String DEFAULT_FREEZE_CONFIG_FILENAME = "cabal.project.freeze";
    public static final String DEFAULT_DESTINATION_DIR = "eta-freeze";

    private final Project project;
    private DirectoryProperty destinationDir;
    private Provider<Set<EtaDependency>> dependencies;

    public EtaResolveDependencies() {
        this.project        = getProject();
        this.dependencies   = getDefaultDependencyProvider();
        this.destinationDir = project.getLayout().directoryProperty();

        destinationDir.set
            (project.getLayout().getBuildDirectory().dir(DEFAULT_DESTINATION_DIR));

        setDescription("Resolve dependencies for all the projects in a multi-project" +
                       " to get a consistent snapshot of all the dependencies.");
    }

    public Provider<Set<EtaDependency>>
        getDefaultDependencyProvider() {
        return project.provider(() -> {
                Set<EtaDependency> allDependencies = new LinkedHashSet<>();
                for (Project p: project.getAllprojects()) {
                    for (Configuration c: p.getConfigurations()) {
                        final EtaConfiguration etaConfiguration =
                            ExtensionHelper.getExtension(c, EtaConfiguration.class);
                        if (etaConfiguration != null) {
                            allDependencies.addAll(etaConfiguration.getAllDependencies());
                        }
                    }
                }
                return allDependencies;
            });
    }

    @Input
    public Set<String> getDependencies() {
        return dependencies.get().stream()
            .map(Object::toString).collect(Collectors.toSet());
    }

    public void setDependencies
        (Provider<Set<EtaDependency>> dependencies) {
        this.dependencies = dependencies;
    }

    @Input
    public File getDestinationDirectory() {
        return destinationDir.getAsFile().get();
    }

    public void setDestinationDirectory(Object dir) {
        destinationDir.set(getProject().file(dir));
    }

    @OutputFile
    public Provider<RegularFile> getFreezeConfigFile() {
        return destinationDir.file(DEFAULT_FREEZE_CONFIG_FILENAME);
    }

    @TaskAction
    public void resolveDependencies() {

        /* Create the destination directory if it doesn't exist. */

        File workingDir = getDestinationDirectory();

        if (!workingDir.exists() && !workingDir.mkdirs()) {
            throw new GradleException("Unable to create destination directory: "
                                     + workingDir.getAbsolutePath());
        }

        /* Delete existing *.cabal files to avoid errors when changing the project
           name. */

        project.delete(project.fileTree(workingDir,
                                        fileTree -> fileTree.include("*.cabal")));

        /* Remove the cabal.project.freeze file from a previous run, if it exists. */

        File existingFreezeFile =
            getFreezeConfigFile().get().getAsFile();

        if (existingFreezeFile.exists() && !existingFreezeFile.delete()) {
            throw new GradleException("Unable to delete existing freeze file: "
                                      + existingFreezeFile.getAbsolutePath());
        }

        /* Generate the .cabal & cabal.project files. */

        DependencyUtils.foldEtaDependencies
            (project,
             dependencies.get(),
             (directDeps, projectDeps) ->
             CabalHelper.generateCabalFile(getProject().getName(),
                                           getProject().getVersion().toString(),
                                           directDeps, workingDir),
             gitDeps -> CabalHelper.generateCabalProjectFile(gitDeps, workingDir));

        /* Fork an etlas process to freeze the dependencies.  */

        EtlasCommand etlas = new EtlasCommand(getProject());
        etlas.getWorkingDirectory().set(workingDir);
        etlas.freeze();
    }
}

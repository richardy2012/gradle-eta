package com.typelead.gradle.eta.internal;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.BiConsumer;

import com.typelead.gradle.eta.api.EtaDependency;
import com.typelead.gradle.eta.api.EtaGitDependency;
import com.typelead.gradle.eta.api.EtaDirectDependency;
import com.typelead.gradle.eta.api.EtaProjectDependency;
import com.typelead.gradle.eta.api.SourceRepository;

public class DependencyUtils {
    public static void foldEtaDependencies
        (Collection<EtaDependency> dependencies,
         BiConsumer<List<String>, List<EtaProjectDependency>> directProjectConsumer,
         Consumer<Set<SourceRepository>> gitConsumer) {

        List<String> directDependencies = new ArrayList<>();
        List<EtaProjectDependency> projectDependencies = new ArrayList<>();
        Set<SourceRepository> gitDependencies = new LinkedHashSet<>();

        for (EtaDependency dependency : dependencies) {
            if (dependency instanceof EtaDirectDependency) {
                directDependencies.add(((EtaDirectDependency) dependency).toString());
            } else if (dependency instanceof EtaProjectDependency) {
                projectDependencies.add(((EtaProjectDependency) dependency));
            } else if (dependency instanceof EtaGitDependency) {
                gitDependencies.add
                    (((EtaGitDependency) dependency).getSourceRepository());
            }
        }

        if (directProjectConsumer != null) {
            directProjectConsumer.accept(directDependencies, projectDependencies);
        }

        if (gitConsumer != null) {
            gitConsumer.accept(gitDependencies);
        }
    }
}
/*
 * Copyright 2023 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.gradle.api.publish.internal.mapping;

import org.gradle.api.artifacts.Configuration;
import org.gradle.api.artifacts.DependencyConstraint;
import org.gradle.api.artifacts.ModuleDependency;
import org.gradle.api.artifacts.ModuleVersionIdentifier;
import org.gradle.api.artifacts.ProjectDependency;
import org.gradle.api.artifacts.component.ComponentSelector;
import org.gradle.api.artifacts.component.ModuleComponentSelector;
import org.gradle.api.artifacts.component.ProjectComponentIdentifier;
import org.gradle.api.artifacts.component.ProjectComponentSelector;
import org.gradle.api.artifacts.result.DependencyResult;
import org.gradle.api.artifacts.result.ResolutionResult;
import org.gradle.api.artifacts.result.ResolvedComponentResult;
import org.gradle.api.artifacts.result.ResolvedDependencyResult;
import org.gradle.api.internal.artifacts.ProjectComponentIdentifierInternal;
import org.gradle.api.internal.artifacts.dependencies.DefaultProjectDependencyConstraint;
import org.gradle.api.internal.artifacts.dependencies.ProjectDependencyInternal;
import org.gradle.api.internal.artifacts.ivyservice.projectmodule.ProjectDependencyPublicationResolver;
import org.gradle.api.publish.internal.validation.VariantWarningCollector;
import org.gradle.internal.component.local.model.ProjectComponentSelectorInternal;
import org.gradle.util.Path;

import javax.annotation.Nullable;
import java.util.Set;

/**
 * A {@link VariantDependencyResolver} that performs version mapping.
 */
public class VersionMappingVariantDependencyResolver implements VariantDependencyResolver {

    private final ProjectDependencyPublicationResolver projectDependencyResolver;
    private final Configuration versionMappingConfiguration;
    private final VariantDependencyResolverFactory.DeclaredVersionTransformer declaredVersionTransformer;

    public VersionMappingVariantDependencyResolver(
        ProjectDependencyPublicationResolver projectDependencyResolver,
        @Nullable Configuration versionMappingConfiguration,
        VariantDependencyResolverFactory.DeclaredVersionTransformer declaredVersionTransformer
    ) {
        this.projectDependencyResolver = projectDependencyResolver;
        this.versionMappingConfiguration = versionMappingConfiguration;
        this.declaredVersionTransformer = declaredVersionTransformer;
    }

    @Override
    public ResolvedCoordinates resolveVariantCoordinates(ModuleDependency dependency, VariantWarningCollector warnings) {
        if (!dependency.getAttributes().isEmpty()) {
            warnings.addUnsupported(String.format("%s:%s:%s declared with Gradle attributes", dependency.getGroup(), dependency.getName(), dependency.getVersion()));
        }
        if (!dependency.getRequestedCapabilities().isEmpty()) {
            warnings.addUnsupported(String.format("%s:%s:%s declared with Gradle capabilities", dependency.getGroup(), dependency.getName(), dependency.getVersion()));
        }

        // Traditional version mapping does not support variant-level precision.
        // Just return the component-level coordinates.
        return resolveComponentCoordinates(dependency);
    }

    @Override
    public ResolvedCoordinates resolveVariantCoordinates(DependencyConstraint dependency, VariantWarningCollector warnings) {
        if (!dependency.getAttributes().isEmpty()) {
            warnings.addUnsupported(String.format("%s:%s:%s declared with Gradle attributes", dependency.getGroup(), dependency.getName(), dependency.getVersion()));
        }

        // Traditional version mapping does not support variant-level precision.
        // Just return the component-level coordinates.
        return resolveComponentCoordinates(dependency);
    }

    @Override
    public ResolvedCoordinates resolveComponentCoordinates(ModuleDependency dependency) {
        if (dependency instanceof ProjectDependency) {
            return resolveProject((ProjectDependency) dependency);
        }
        return resolveModule(dependency.getGroup(), dependency.getName(), dependency.getVersion());
    }

    @Override
    public ResolvedCoordinates resolveComponentCoordinates(DependencyConstraint dependency) {
        if (dependency instanceof DefaultProjectDependencyConstraint) {
            return resolveProject(((DefaultProjectDependencyConstraint) dependency).getProjectDependency());
        }
        return resolveModule(dependency.getGroup(), dependency.getName(), dependency.getVersion());
    }

    public ResolvedCoordinates resolveProject(ProjectDependency dependency) {
        Path identityPath = ((ProjectDependencyInternal) dependency).getIdentityPath();
        ModuleVersionIdentifier coordinates = projectDependencyResolver.resolve(ModuleVersionIdentifier.class, identityPath);
        ModuleVersionIdentifier resolved = maybeResolveVersion(coordinates.getGroup(), coordinates.getName(), identityPath);
        return ResolvedCoordinates.from(resolved != null ? resolved : coordinates);
    }

    private ResolvedCoordinates resolveModule(String group, String name, @Nullable String declaredVersion) {
        ModuleVersionIdentifier resolved = maybeResolveVersion(group, name, null);
        return resolved != null
            ? ResolvedCoordinates.from(resolved)
            : ResolvedCoordinates.create(group, name, declaredVersionTransformer.transform(group, name, declaredVersion));
    }

    @Nullable
    public ModuleVersionIdentifier maybeResolveVersion(String group, String module, @Nullable Path identityPath) {
        if (versionMappingConfiguration == null) {
            return null;
        }

        ResolutionResult resolutionResult = versionMappingConfiguration
            .getIncoming()
            .getResolutionResult();
        Set<? extends ResolvedComponentResult> resolvedComponentResults = resolutionResult.getAllComponents();
        for (ResolvedComponentResult selected : resolvedComponentResults) {
            ModuleVersionIdentifier moduleVersion = selected.getModuleVersion();
            if (moduleVersion != null && group.equals(moduleVersion.getGroup()) && module.equals(moduleVersion.getName())) {
                return moduleVersion;
            }
        }

        // If we reach this point it means we have a dependency which doesn't belong to the resolution result
        // Which can mean two things:
        // 1. the graph used to get the resolved version has nothing to do with the dependencies we're trying to get versions for (likely user error)
        // 2. the graph contains first-level dependencies which have been substituted (likely) so we're going to iterate on dependencies instead
        Set<? extends DependencyResult> allDependencies = resolutionResult.getAllDependencies();
        for (DependencyResult dependencyResult : allDependencies) {
            if (dependencyResult instanceof ResolvedDependencyResult) {
                ComponentSelector rcs = dependencyResult.getRequested();
                ResolvedComponentResult selected = ((ResolvedDependencyResult) dependencyResult).getSelected();
                if (rcs instanceof ModuleComponentSelector) {
                    ModuleComponentSelector requested = (ModuleComponentSelector) rcs;
                    if (requested.getGroup().equals(group) && requested.getModule().equals(module)) {
                        return getModuleVersionId(selected);
                    }
                } else if (rcs instanceof ProjectComponentSelector) {
                    ProjectComponentSelectorInternal pcs = (ProjectComponentSelectorInternal) rcs;
                    if (pcs.getIdentityPath().equals(identityPath)) {
                        return getModuleVersionId(selected);
                    }
                }
            }
        }

        return null;
    }

    @Nullable
    private ModuleVersionIdentifier getModuleVersionId(ResolvedComponentResult selected) {
        // Match found - need to make sure that if the selection is a project, we use its publication identity
        if (selected.getId() instanceof ProjectComponentIdentifier) {
            Path identityPath = ((ProjectComponentIdentifierInternal) selected.getId()).getIdentityPath();
            return projectDependencyResolver.resolve(ModuleVersionIdentifier.class, identityPath);
        }
        return selected.getModuleVersion();
    }
}

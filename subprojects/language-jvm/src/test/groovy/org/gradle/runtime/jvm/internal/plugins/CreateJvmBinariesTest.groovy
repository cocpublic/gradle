/*
 * Copyright 2014 the original author or authors.
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

package org.gradle.runtime.jvm.internal.plugins
import org.gradle.language.base.LanguageSourceSet
import org.gradle.runtime.base.BinaryContainer
import org.gradle.runtime.base.NamedProjectComponentIdentifier
import org.gradle.runtime.base.ProjectBinary
import org.gradle.runtime.base.internal.BinaryNamingScheme
import org.gradle.runtime.base.internal.BinaryNamingSchemeBuilder
import org.gradle.runtime.jvm.ProjectJvmLibrary
import org.gradle.runtime.jvm.internal.DefaultProjectJarBinary
import org.gradle.runtime.jvm.internal.DefaultProjectJvmLibrary
import spock.lang.Specification

import static org.gradle.util.WrapUtil.toNamedDomainObjectSet

class CreateJvmBinariesTest extends Specification {
    def buildDir = new File("buildDir")
    def namingSchemeBuilder = Mock(BinaryNamingSchemeBuilder)
    def rule = new CreateJvmBinaries(namingSchemeBuilder, buildDir)
    def binaries = Mock(BinaryContainer)

    def "adds a binary for each jvm library"() {
        def library = new DefaultProjectJvmLibrary(componentId("jvmLibOne", ":project-path"))
        def namingScheme = Mock(BinaryNamingScheme)

        when:
        rule.createBinaries(binaries, toNamedDomainObjectSet(ProjectJvmLibrary, library))

        then:
        _ * namingScheme.description >> "jvmLibJar"
        _ * namingScheme.outputDirectoryBase >> "jvmJarOutput"
        1 * namingSchemeBuilder.withComponentName("jvmLibOne") >> namingSchemeBuilder
        1 * namingSchemeBuilder.withTypeString("jar") >> namingSchemeBuilder
        1 * namingSchemeBuilder.build() >> namingScheme
        1 * binaries.add({ DefaultProjectJarBinary binary ->
            binary.namingScheme == namingScheme
            binary.library == library
            binary.classesDir == new File(buildDir, "jvmJarOutput")
            binary.resourcesDir == binary.classesDir
        } as ProjectBinary)
        0 * _
    }

    def "created binary has sources from jvm library"() {
        def library = new DefaultProjectJvmLibrary(componentId("jvmLibOne", ":project-path"))
        def namingScheme = Mock(BinaryNamingScheme)
        def source1 = Mock(LanguageSourceSet)
        def source2 = Mock(LanguageSourceSet)

        when:
        library.source([source1, source2])
        rule.createBinaries(binaries, toNamedDomainObjectSet(ProjectJvmLibrary, library))

        then:
        _ * namingScheme.description >> "jvmLibJar"
        _ * namingScheme.outputDirectoryBase >> "jvmJarOutput"
        1 * namingSchemeBuilder.withComponentName("jvmLibOne") >> namingSchemeBuilder
        1 * namingSchemeBuilder.withTypeString("jar") >> namingSchemeBuilder
        1 * namingSchemeBuilder.build() >> namingScheme
        1 * binaries.add({ DefaultProjectJarBinary binary ->
            binary.namingScheme == namingScheme
            binary.library == library
            binary.source == library.source
        } as ProjectBinary)
        0 * _
    }

    def componentId(def name, def path) {
        Stub(NamedProjectComponentIdentifier) {
            getName() >> name
            getProjectPath() >> path
        }
    }
}

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

package org.gradle.integtests.tooling.r84

import org.apache.commons.io.output.TeeOutputStream
import org.gradle.integtests.fixtures.jvm.JDWPUtil
import org.gradle.integtests.tooling.fixture.TargetGradleVersion
import org.gradle.integtests.tooling.fixture.ToolingApiSpecification
import org.gradle.test.fixtures.file.TestFile
import org.gradle.tooling.ProjectConnection
import spock.lang.Issue

import java.util.function.Function

@TargetGradleVersion('>=8.4')
class TestLauncherDebugCrossVersionTest extends ToolingApiSpecification {

    JDWPUtil javaDebugFixture

    def setup() {
        javaDebugFixture = new JDWPUtil(5005)
        javaDebugFixture.listen()
    }

    def cleanup() {
        javaDebugFixture.close()
    }

    @Issue('https://github.com/gradle/gradle/issues/26366')
    def "Run test twice (#scenarioName)"() {
        given:
        def runTestClass = withSampleBuildTest()

        when:
        def output1 = runTestClass.apply(firstDebug)

        then:
        notThrown(Exception)
        assertTestDebugConfigWas(output1, firstDebug)

        when:
        def output2 = runTestClass.apply(secondDebug)

        then:
        notThrown(Exception)
        assertTestDebugConfigWas(output2, secondDebug)

        where:
        scenarioName                     | firstDebug | secondDebug
        "first wo/debug, second w/debug" | false      | true
        "first w/debug, second wo/debug" | true       | false

    }

    private Function<Boolean, String> withSampleBuildTest() {
        settingsFile << "include('app')"
        javaBuildWithTests(file('app'))
        return { boolean debugMode ->
            runTaskAndTestClassUsing(debugMode, ':app:test', "TestClass1")
        }
    }

    private String runTaskAndTestClassUsing(boolean debugMode, String task, String testClass) {
        withConnection { ProjectConnection connection ->
            def stdout = new ByteArrayOutputStream()
            def tee = new TeeOutputStream(System.out, stdout)
            def testLauncher = connection.newTestLauncher()
                .withTaskAndTestClasses(task, Arrays.asList(testClass))
                .setStandardOutput(tee)
            if (debugMode) {
                testLauncher.debugTestsOn(javaDebugFixture.port)
            }
            testLauncher.run()
            stdout.toString('utf-8')
        }
    }

    private void assertTestDebugConfigWas(String output, boolean debugMode) {
        assert output.contains("Debug mode enabled: $debugMode")
    }

    private void javaBuildWithTests(TestFile projectDir) {
        projectDir.file('settings.gradle') << ''
        javaLibraryWithTests(projectDir)
    }

    private void javaLibraryWithTests(TestFile projectDir) {
        propertiesFile << 'org.gradle.configuration-cache=true'
        projectDir.file('build.gradle') << '''
            plugins {
                id 'java-library'
            }
            repositories {
                mavenCentral()
            }
            testing {
                suites {
                    test {
                        useJUnitJupiter()
                    }
                }
            }
            tasks.named('test') {
                testLogging {
                    showStandardStreams = true
                }
                doFirst {
                    System.out.println("Debug mode enabled: " + debugOptions.enabled.get())
                }
            }
        '''
        writeTestClass(projectDir, 'TestClass1')
        writeTestClass(projectDir, 'TestClass2')
    }

    private TestFile writeTestClass(TestFile projectDir, String testClassName) {
        projectDir.file("src/test/java/${testClassName}.java") << """
            public class $testClassName {
                @org.junit.jupiter.api.Test
                void testMethod() {
                    System.out.println("${testClassName}.testMethod");
                }
            }
        """
    }
}

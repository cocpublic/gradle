/*
 * Copyright 2010 the original author or authors.
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
package org.gradle.launcher.cli

import org.apache.tools.ant.Main
import org.gradle.api.Action
import org.gradle.cli.CommandLineArgumentException
import org.gradle.cli.CommandLineParser
import org.gradle.internal.Factory
import org.gradle.internal.jvm.Jvm
import org.gradle.internal.logging.LoggingManagerInternal
import org.gradle.internal.logging.events.OutputEventListener
import org.gradle.internal.logging.progress.ProgressLoggerFactory
import org.gradle.internal.logging.services.LoggingServiceRegistry
import org.gradle.internal.logging.text.StreamingStyledTextOutput
import org.gradle.internal.logging.text.StyledTextOutput
import org.gradle.internal.logging.text.StyledTextOutputFactory
import org.gradle.internal.os.OperatingSystem
import org.gradle.internal.service.ServiceRegistry
import org.gradle.launcher.bootstrap.CommandLineActionFactory
import org.gradle.launcher.bootstrap.ExecutionListener
import org.gradle.test.fixtures.file.TestNameTestDirectoryProvider
import org.gradle.util.SetSystemProperties
import org.gradle.util.internal.DefaultGradleVersion
import org.gradle.util.internal.RedirectStdOutAndErr
import org.junit.Rule
import spock.lang.Specification

class DefaultCommandLineActionFactoryTest extends Specification {
    @Rule
    public final RedirectStdOutAndErr outputs = new RedirectStdOutAndErr();
    @Rule
    public final SetSystemProperties sysProperties = new SetSystemProperties();
    @Rule
    TestNameTestDirectoryProvider tmpDir = new TestNameTestDirectoryProvider(getClass());
    final ExecutionListener executionListener = Mock()
    final LoggingServiceRegistry loggingServices = Mock()
    final LoggingManagerInternal loggingManager = Mock()
    final CommandLineActionCreator actionFactory1 = Mock()
    final CommandLineActionCreator actionFactory2 = Mock()
    final CommandLineActionFactory factory = new DefaultCommandLineActionFactory() {
        @Override
        LoggingServiceRegistry createLoggingServices() {
            return loggingServices
        }

        @Override
        protected void createBuildActionFactoryActionCreator(ServiceRegistry loggingServices, List<CommandLineActionCreator> actionCreators) {
            actionCreators.add(actionFactory1)
            actionCreators.add(actionFactory2)
        }
    }

    def setup() {
        ProgressLoggerFactory progressLoggerFactory = Mock()
        _ * loggingServices.get(ProgressLoggerFactory) >> progressLoggerFactory
        _ * loggingServices.get(OutputEventListener) >> Mock(OutputEventListener)
        Factory<LoggingManagerInternal> loggingManagerFactory = Mock()
        _ * loggingServices.getFactory(LoggingManagerInternal) >> loggingManagerFactory
        _ * loggingManagerFactory.create() >> loggingManager
        StyledTextOutputFactory textOutputFactory = Mock()
        _ * loggingServices.get(StyledTextOutputFactory) >> textOutputFactory
        StyledTextOutput textOutput = new StreamingStyledTextOutput(outputs.stdErrPrintStream)
        _ * textOutputFactory.create(_, _) >> textOutput
    }

    def "delegates to each action factory to configure the command-line parser and create the action"() {
        def rawAction = Mock(Action<? super ExecutionListener>)

        when:
        def action = factory.convert(["--some-option"])

        then:
        action

        when:
        action.execute(executionListener)

        then:
        1 * actionFactory1.configureCommandLineParser(!null) >> { CommandLineParser parser -> parser.option("some-option") }
        1 * actionFactory2.configureCommandLineParser(!null)
        1 * actionFactory1.createAction(!null, !null) >> rawAction
        1 * rawAction.execute(executionListener)
    }

    def "configures logging before parsing command-line"() {
        Action<ExecutionListener> rawAction = Mock()

        when:
        def action = factory.convert([])

        then:
        action

        when:
        action.execute(executionListener)

        then:
        1 * loggingManager.start()

        and:
        1 * actionFactory1.configureCommandLineParser(!null)
        1 * actionFactory2.configureCommandLineParser(!null)
        1 * actionFactory1.createAction(!null, !null) >> rawAction
    }

    def "reports command-line parse failure"() {
        when:
        def action = factory.convert(['--broken'])
        action.execute(executionListener)

        then:
        outputs.stdErr.contains('--broken')
        outputs.stdErr.contains('USAGE: gradle [option...] [task...]')
        outputs.stdErr.contains('--help')
        outputs.stdErr.contains('--some-option')

        and:
        1 * actionFactory1.configureCommandLineParser(!null) >> {CommandLineParser parser -> parser.option('some-option')}
        1 * executionListener.onFailure({it instanceof CommandLineArgumentException})
        0 * executionListener._
    }

    def "reports failure to build action due to command-line parse failure"() {
        def failure = new CommandLineArgumentException("<broken>")

        when:
        def action = factory.convert(['--some-option'])
        action.execute(executionListener)

        then:
        outputs.stdErr.contains('<broken>')
        outputs.stdErr.contains('USAGE: gradle [option...] [task...]')
        outputs.stdErr.contains('--help')
        outputs.stdErr.contains('--some-option')

        and:
        1 * actionFactory1.configureCommandLineParser(!null) >> {CommandLineParser parser -> parser.option('some-option')}
        1 * actionFactory1.createAction(!null, !null) >> { throw failure }
        1 * executionListener.onFailure(failure)
        0 * executionListener._
    }

    def "continues on failure to parse logging configuration"() {
        when:
        def action = factory.convert(["--logging=broken"])
        action.execute(executionListener)

        then:
        outputs.stdErr.contains('--logging')
        outputs.stdErr.contains('USAGE: gradle [option...] [task...]')
        outputs.stdErr.contains('--help')
        outputs.stdErr.contains('--some-option')

        and:
        1 * actionFactory1.configureCommandLineParser(!null) >> {CommandLineParser parser -> parser.option('some-option')}
        1 * executionListener.onFailure({it instanceof CommandLineArgumentException})
        0 * executionListener._
    }

    def "reports other failure to build action"() {
        def failure = new RuntimeException("<broken>")

        when:
        def action = factory.convert([])
        action.execute(executionListener)

        then:
        outputs.stdErr.contains('<broken>')

        and:
        1 * actionFactory1.createAction(!null, !null) >> { throw failure }
        1 * executionListener.onFailure(failure)
        0 * executionListener._
    }

    def "displays usage message"() {
        when:
        def action = factory.convert([option])
        action.execute(executionListener)

        then:
        outputs.stdOut.contains('USAGE: gradle [option...] [task...]')
        outputs.stdOut.contains('--help')
        outputs.stdOut.contains('--some-option')

        and:
        1 * actionFactory1.configureCommandLineParser(!null) >> {CommandLineParser parser -> parser.option('some-option')}
        0 * executionListener._

        where:
        option << ['-h', '-?', '--help']
    }

    def "uses system property for application name"() {
        System.setProperty("org.gradle.appname", "gradle-app");

        when:
        def action = factory.convert(['-?'])
        action.execute(executionListener)

        then:
        outputs.stdOut.contains('USAGE: gradle-app [option...] [task...]')
    }

    def "displays version message"() {
        def version = DefaultGradleVersion.current()
        def expectedText = [
            "",
            "------------------------------------------------------------",
            "Gradle ${version.version}",
            "------------------------------------------------------------",
            "",
            "Build time:   $version.buildTimestamp",
            "Revision:     $version.gitRevision",
            "",
            "Kotlin:       ${KotlinDslVersion.current().kotlinVersion}",
            "Groovy:       $GroovySystem.version",
            "Ant:          $Main.antVersion",
            "JVM:          ${Jvm.current()}",
            "OS:           ${OperatingSystem.current()}",
            ""
        ].join(System.lineSeparator())

        when:
        def action = factory.convert(options)
        action.execute(executionListener)

        then:
        outputs.stdOut.contains(expectedText)

        and:
        1 * actionFactory1.configureCommandLineParser(!null) >> {CommandLineParser parser -> parser.option('some-option')}
        0 * actionFactory1.createAction(_, _)
        1 * loggingManager.start()
        0 * executionListener._

        where:
        options << [['-v', '--version'], ['', '--some-option']].combinations().collect { it.findAll() }
    }

    def "displays version message and continues build"() {
        def version = DefaultGradleVersion.current()
        def expectedText = [
            "",
            "------------------------------------------------------------",
            "Gradle ${version.version}",
            "------------------------------------------------------------",
            "",
            "Build time:   $version.buildTimestamp",
            "Revision:     $version.gitRevision",
            "",
            "Kotlin:       ${KotlinDslVersion.current().kotlinVersion}",
            "Groovy:       $GroovySystem.version",
            "Ant:          $Main.antVersion",
            "JVM:          ${Jvm.current()}",
            "OS:           ${OperatingSystem.current()}",
            ""
        ].join(System.lineSeparator())
        final CommandLineActionFactory factoryWithComposer = new DefaultCommandLineActionFactory() {
            @Override
            LoggingServiceRegistry createLoggingServices() {
                return loggingServices
            }

            @Override
            protected void createBuildActionFactoryActionCreator(ServiceRegistry loggingServices, List<CommandLineActionCreator> actionCreators) {
                actionCreators.add(new DefaultCommandLineActionFactory.ComposingCreator(actionCreators));
                actionCreators.add(actionFactory1)
            }
        }

        when:
        def action = factoryWithComposer.convert(options)
        action.execute(executionListener)

        then:
        outputs.stdOut.contains(expectedText)
        !actionCalled || outputs.stdOut.contains("action1")

        and:
        actionCalled * actionFactory1.createAction(!null, !null) >> {
            { println "action1" } as Action<? super ExecutionListener>
        }
        1 * loggingManager.start()
        0 * executionListener._

        where:
        options            | actionCalled
        ['-v']             | 0
        ['-V']             | 1
        ['--show-version'] | 1
    }
}

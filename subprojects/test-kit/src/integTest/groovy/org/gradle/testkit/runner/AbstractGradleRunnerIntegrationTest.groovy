/*
 * Copyright 2015 the original author or authors.
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

package org.gradle.testkit.runner

import org.gradle.integtests.fixtures.daemon.DaemonLogsAnalyzer
import org.gradle.integtests.fixtures.daemon.DaemonsFixture
import org.gradle.integtests.fixtures.executer.IntegrationTestBuildContext
import org.gradle.test.fixtures.file.TestFile
import org.gradle.test.fixtures.file.TestNameTestDirectoryProvider
import org.gradle.testkit.runner.internal.DefaultGradleRunner
import org.gradle.testkit.runner.internal.GradleExecutor
import org.junit.Rule
import spock.lang.Shared
import spock.lang.Specification

abstract class AbstractGradleRunnerIntegrationTest extends Specification {

    @Shared
    IntegrationTestBuildContext buildContext = new IntegrationTestBuildContext()

    @Rule
    TestNameTestDirectoryProvider testProjectDir = new TestNameTestDirectoryProvider()

    File buildFile

    TestFile getTestKitWorkspace() {
        testProjectDir.file("test-kit-workspace")
    }

    def setup() {
        buildFile = file('build.gradle')
    }

    TestFile file(String path) {
        testProjectDir.file(path)
    }

    protected DefaultGradleRunner runner(List<String> arguments) {
        return runner(arguments as String[])
    }

    DefaultGradleRunner runner(String... arguments) {
        new DefaultGradleRunner(buildContext.gradleHomeDir)
            .withGradleUserHomeDir(testKitWorkspace)
            .withProjectDir(testProjectDir.testDirectory)
            .withArguments(arguments) as DefaultGradleRunner
    }

    static String helloWorldTask() {
        """
        task helloWorld {
            doLast {
                println 'Hello world!'
            }
        }
        """
    }

    DaemonsFixture daemons(File gradleUserHomeDir, String daemonDir = 'daemon') {
        DaemonLogsAnalyzer.newAnalyzer(new File(gradleUserHomeDir, daemonDir), buildContext.version.version)
    }

    DaemonsFixture daemons() {
        daemons(testKitWorkspace, GradleExecutor.TEST_KIT_DAEMON_DIR_NAME)
    }

    def cleanup() {
        daemons().killAll()
    }
}

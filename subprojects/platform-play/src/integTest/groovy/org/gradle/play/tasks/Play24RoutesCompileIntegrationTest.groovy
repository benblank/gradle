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

package org.gradle.play.tasks

import org.gradle.integtests.fixtures.TargetCoverage
import org.gradle.play.integtest.fixtures.PlayCoverage
import org.gradle.util.Requires
import org.gradle.util.TestPrecondition

@TargetCoverage({ PlayCoverage.PLAY24_OR_LATER })
@Requires(TestPrecondition.JDK8_OR_LATER)
class Play24RoutesCompileIntegrationTest extends AbstractRoutesCompileIntegrationTest {
    @Override
    def getRoutesJavaFileNameTemplate(String packageName, String namespace) {
        return "${namespace ? namespace + '/' :''}controllers/${packageName ? packageName + '/' :''}routes.java"
    }

    @Override
    def getRoutesReverseFileNameTemplate(String packageName, String namespace) {
        return "${namespace ? namespace + '/' :''}controllers/${packageName ? packageName + '/' :''}ReverseRoutes.scala"
    }

    @Override
    def getRoutesScalaFileNameTemplate(String packageName, String namespace) {
        return "${packageName?:'router'}/Routes.scala"
    }

    @Override
    def getOtherRoutesFilesTemplates() {
        return [
            {packageName, namespace -> "${namespace ? namespace + '/' :''}controllers/${packageName ? packageName + '/' :''}javascript/JavaScriptReverseRoutes.scala" },
            {packageName, namespace -> "${packageName?:'router'}/RoutesPrefix.scala" }
        ]
    }

    def "can specify route compiler type as injected"() {
        given:
        withRoutesTemplate()
        file("app/controllers/Application.scala").with {
            // change Scala companion object into a regular class
            text = text.replaceFirst(/object/, "class")
        }
        buildFile << """
model {
    components {
        play {
            injectedRoutesGenerator = true
        }
    }
}
"""
        expect:
        succeeds("compilePlayBinaryScala")
        and:
        destinationDir.assertHasDescendants(createRouteFileList() as String[])
    }

    def "can change route compiler type after compiling once"() {
        when:
        withRoutesTemplate()
        then:
        succeeds("compilePlayBinaryScala")

        when:
        file("app/controllers/Application.scala").with {
            // change Scala companion object into a regular class
            text = text.replaceFirst(/object/, "class")
        }
        buildFile << """
model {
    components {
        play {
            injectedRoutesGenerator = true
        }
    }
}
"""
        then:
        succeeds("compilePlayBinaryScala")
        executedTasks.contains(":compilePlayBinaryRoutes")
        and:
        destinationDir.assertHasDescendants(createRouteFileList() as String[])
    }
}

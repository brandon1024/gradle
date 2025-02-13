/*
 * Copyright 2018 the original author or authors.
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

package org.gradle.api.plugins

import org.gradle.integtests.fixtures.AbstractIntegrationSpec

class JavaBasePluginIntegrationTest extends AbstractIntegrationSpec {

    def "can define and build a source set with implementation dependencies"() {
        settingsFile << """
            include 'main', 'tests'
        """
        buildFile << """
            project(':main') {
                apply plugin: 'java'
            }
            project(':tests') {
                apply plugin: 'java-base'
                sourceSets {
                    unitTest {
                    }
                }
                dependencies {
                    unitTestImplementation project(':main')
                }
            }
        """
        file("main/src/main/java/Main.java") << """public class Main { }"""
        file("tests/src/unitTest/java/Test.java") << """public class Test { Main main = null; }"""

        expect:
        succeeds(":test:unitTestClasses")
        file("main/build/classes/java/main").assertHasDescendants("Main.class")
        file("tests/build/classes/java/unitTest").assertHasDescendants("Test.class")
    }
}

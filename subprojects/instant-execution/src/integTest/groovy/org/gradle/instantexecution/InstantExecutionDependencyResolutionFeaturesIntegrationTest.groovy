/*
 * Copyright 2020 the original author or authors.
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

package org.gradle.instantexecution

import org.gradle.api.tasks.TasksWithInputsAndOutputs
import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import org.gradle.test.fixtures.server.http.HttpServer
import org.gradle.test.fixtures.server.http.MavenHttpRepository
import org.junit.Rule
import spock.lang.Unroll

import java.util.concurrent.TimeUnit

class InstantExecutionDependencyResolutionFeaturesIntegrationTest extends AbstractInstantExecutionIntegrationTest implements TasksWithInputsAndOutputs {
    @Rule
    HttpServer server = new HttpServer()
    def remoteRepo = new MavenHttpRepository(server, mavenRepo)

    @Override
    def setup() {
        // So that dependency resolution results from previous tests do not interfere
        executer.requireOwnGradleUserHomeDir()
    }

    def "does not invalidate configuration cache entry when dynamic version information has not expired"() {
        given:
        server.start()

        remoteRepo.module("thing", "lib", "1.2").publish()
        def v3 = remoteRepo.module("thing", "lib", "1.3").publish()

        taskTypeLogsInputFileCollectionContent()
        buildFile << """
            configurations {
                implementation
            }

            repositories { maven { url = '${remoteRepo.uri}' } }

            dependencies {
                implementation 'thing:lib:1.+'
            }

            task resolve1(type: ShowFilesTask) {
                inFiles.from(configurations.implementation)
            }
            task resolve2(type: ShowFilesTask) {
                inFiles.from(configurations.implementation)
            }
        """
        def fixture = newInstantExecutionFixture()

        remoteRepo.getModuleMetaData("thing", "lib").expectGet()
        v3.pom.expectGet()
        v3.artifact.expectGet()

        when:
        instantRun("resolve1")

        then:
        fixture.assertStateStored()
        outputContains("result = [lib-1.3.jar]")

        when:
        instantRun("resolve1")

        then:
        fixture.assertStateLoaded()
        outputContains("result = [lib-1.3.jar]")

        when: // run again with different tasks, to verify behaviour when version list is already cached when configuration cache entry is written
        instantRun("resolve2")

        then:
        fixture.assertStateStored()
        outputContains("result = [lib-1.3.jar]")

        when:
        instantRun("resolve2")

        then:
        fixture.assertStateLoaded()
        outputContains("result = [lib-1.3.jar]")
    }

    def "invalidates configuration cache entry when dynamic version information has expired"() {
        given:
        server.start()

        remoteRepo.module("thing", "lib", "1.2").publish()
        def v3 = remoteRepo.module("thing", "lib", "1.3").publish()

        taskTypeLogsInputFileCollectionContent()
        buildFile << """
            configurations {
                implementation {
                    resolutionStrategy.cacheDynamicVersionsFor(4, ${TimeUnit.name}.HOURS)
                }
            }

            repositories { maven { url = '${remoteRepo.uri}' } }

            dependencies {
                implementation 'thing:lib:1.+'
            }

            task resolve1(type: ShowFilesTask) {
                inFiles.from(configurations.implementation)
            }
            task resolve2(type: ShowFilesTask) {
                inFiles.from(configurations.implementation)
            }
        """
        def fixture = newInstantExecutionFixture()

        remoteRepo.getModuleMetaData("thing", "lib").expectGet()
        v3.pom.expectGet()
        v3.artifact.expectGet()

        when:
        instantRun("resolve1")

        then:
        fixture.assertStateStored()
        outputContains("result = [lib-1.3.jar]")

        when: // run again with different tasks, to verify behaviour when version list is already cached when configuration cache entry is written
        instantRun("resolve2")

        then:
        fixture.assertStateStored()
        outputContains("result = [lib-1.3.jar]")

        when:
        def clockOffset = TimeUnit.MILLISECONDS.convert(4, TimeUnit.HOURS)
        remoteRepo.getModuleMetaData("thing", "lib").expectHead()
        instantRun("resolve1", "-Dorg.gradle.internal.test.clockoffset=${clockOffset}")

        then:
        fixture.assertStateStored()
        outputContains("Calculating task graph as configuration cache cannot be reused because cached version information for thing:lib:1.+ has expired.")
        outputContains("result = [lib-1.3.jar]")

        when:
        instantRun("resolve2", "-Dorg.gradle.internal.test.clockoffset=${clockOffset}")

        then:
        fixture.assertStateStored()
        outputContains("Calculating task graph as configuration cache cannot be reused because cached version information for thing:lib:1.+ has expired.")
        outputContains("result = [lib-1.3.jar]")
    }

    def "does not invalidate configuration cache entry when changing artifact information has not expired"() {
        given:
        server.start()

        def v3 = remoteRepo.module("thing", "lib", "1.3").publish()

        taskTypeLogsInputFileCollectionContent()
        buildFile << """
            configurations {
                implementation
            }

            repositories { maven { url = '${remoteRepo.uri}' } }

            dependencies {
                implementation('thing:lib:1.3') {
                    changing = true
                }
            }

            task resolve1(type: ShowFilesTask) {
                inFiles.from(configurations.implementation)
            }
            task resolve2(type: ShowFilesTask) {
                inFiles.from(configurations.implementation)
            }
        """
        def fixture = newInstantExecutionFixture()

        v3.pom.expectGet()
        v3.artifact.expectGet()

        when:
        instantRun("resolve1")

        then:
        fixture.assertStateStored()
        outputContains("result = [lib-1.3.jar]")

        when:
        instantRun("resolve1")

        then:
        fixture.assertStateLoaded()
        outputContains("result = [lib-1.3.jar]")

        when: // run again with different tasks, to verify behaviour when artifact information is cached
        instantRun("resolve2")

        then:
        fixture.assertStateStored()
        outputContains("result = [lib-1.3.jar]")

        when:
        instantRun("resolve2")

        then:
        fixture.assertStateLoaded()
        outputContains("result = [lib-1.3.jar]")
    }

    def "invalidates configuration cache entry when changing artifact information has expired"() {
        given:
        server.start()

        def v3 = remoteRepo.module("thing", "lib", "1.3").publish()

        taskTypeLogsInputFileCollectionContent()
        buildFile << """
            configurations {
                implementation {
                    resolutionStrategy.cacheChangingModulesFor(4, ${TimeUnit.name}.HOURS)
                }
            }

            repositories { maven { url = '${remoteRepo.uri}' } }

            dependencies {
                implementation('thing:lib:1.3') {
                    changing = true
                }
            }

            task resolve1(type: ShowFilesTask) {
                inFiles.from(configurations.implementation)
            }
            task resolve2(type: ShowFilesTask) {
                inFiles.from(configurations.implementation)
            }
        """
        def fixture = newInstantExecutionFixture()

        v3.pom.expectGet()
        v3.artifact.expectGet()

        when:
        instantRun("resolve1")

        then:
        fixture.assertStateStored()
        outputContains("result = [lib-1.3.jar]")

        when: // run again with different tasks, to verify behaviour when artifact information is cached
        instantRun("resolve2")

        then:
        fixture.assertStateStored()
        outputContains("result = [lib-1.3.jar]")

        when:
        v3.pom.expectHead()
        v3.artifact.expectHead()
        def clockOffset = TimeUnit.MILLISECONDS.convert(4, TimeUnit.HOURS)
        instantRun("resolve1", "-Dorg.gradle.internal.test.clockoffset=${clockOffset}")

        then:
        fixture.assertStateStored()
        outputContains("Calculating task graph as configuration cache cannot be reused because cached artifact information for thing:lib:1.3 has expired.")
        outputContains("result = [lib-1.3.jar]")

        when:
        instantRun("resolve2", "-Dorg.gradle.internal.test.clockoffset=${clockOffset}")

        then:
        fixture.assertStateStored()
        outputContains("Calculating task graph as configuration cache cannot be reused because cached artifact information for thing:lib:1.3 has expired.")
        outputContains("result = [lib-1.3.jar]")
    }

    // This documents current behaviour, rather than desired behaviour. The contents of the artifact does not affect the contents of the task graph and so should not be treated as an input
    @Unroll
    def "reports changes to artifact in #repo.displayName"() {
        repo.setup(this)
        taskTypeLogsInputFileCollectionContent()
        buildFile << """
            configurations {
                resolve1
                resolve2
            }
            dependencies {
                resolve1 'thing:lib1:2.1'
                resolve2 'thing:lib1:2.1'
            }

            task resolve1(type: ShowFilesTask) {
                inFiles.from(configurations.resolve1)
            }
            task resolve2(type: ShowFilesTask) {
                inFiles.from(configurations.resolve2)
            }
        """
        def fixture = newInstantExecutionFixture()

        when:
        instantRun("resolve1", "resolve2")

        then:
        fixture.assertStateStored()
        outputContains("result = [lib1-2.1.jar]")

        when:
        instantRun("resolve1", "resolve2")

        then:
        fixture.assertStateLoaded()
        outputContains("result = [lib1-2.1.jar]")

        when:
        repo.publishWithDifferentArtifactContent(this)
        instantRun("resolve1", "resolve2")

        then:
        fixture.assertStateStored()
        outputContains("Calculating task graph as configuration cache cannot be reused because file '${repo.pomLocation}' has changed.")
        outputContains("result = [lib1-2.1.jar]")

        when:
        instantRun("resolve1", "resolve2")

        then:
        fixture.assertStateLoaded()
        outputContains("result = [lib1-2.1.jar]")

        where:
        repo                 | _
        new MavenFileRepo()  | _
        new MavenLocalRepo() | _
    }

    @Unroll
    def "reports changes to metadata in #repo.displayName"() {
        repo.setup(this)
        taskTypeLogsInputFileCollectionContent()
        buildFile << """
            configurations {
                resolve1
                resolve2
            }
            dependencies {
                resolve1 'thing:lib1:2.1'
                resolve2 'thing:lib1:2.1'
            }

            task resolve1(type: ShowFilesTask) {
                inFiles.from(configurations.resolve1)
            }
            task resolve2(type: ShowFilesTask) {
                inFiles.from(configurations.resolve2)
            }
        """
        def fixture = newInstantExecutionFixture()

        when:
        instantRun("resolve1", "resolve2")

        then:
        fixture.assertStateStored()
        outputContains("result = [lib1-2.1.jar]")

        when:
        instantRun("resolve1", "resolve2")

        then:
        fixture.assertStateLoaded()
        outputContains("result = [lib1-2.1.jar]")

        when:
        repo.publishWithDifferentDependencies(this)
        instantRun("resolve1", "resolve2")

        then:
        fixture.assertStateStored()
        outputContains("Calculating task graph as configuration cache cannot be reused because file '${repo.pomLocation}' has changed.")
        outputContains("result = [lib1-2.1.jar, lib2-4.0.jar]")

        when:
        instantRun("resolve1", "resolve2")

        then:
        fixture.assertStateLoaded()
        outputContains("result = [lib1-2.1.jar, lib2-4.0.jar]")

        where:
        repo                 | _
        new MavenFileRepo()  | _
        new MavenLocalRepo() | _
    }

    @Unroll
    def "reports changes to matching versions in #repo.displayName"() {
        repo.setup(this)
        taskTypeLogsInputFileCollectionContent()
        buildFile << """
            configurations {
                resolve1
                resolve2
            }
            dependencies {
                resolve1 'thing:lib1:2.+'
                resolve2 'thing:lib1:2.+'
            }

            task resolve1(type: ShowFilesTask) {
                inFiles.from(configurations.resolve1)
            }
            task resolve2(type: ShowFilesTask) {
                inFiles.from(configurations.resolve2)
            }
        """
        def fixture = newInstantExecutionFixture()

        when:
        instantRun("resolve1", "resolve2")

        then:
        fixture.assertStateStored()
        outputContains("result = [lib1-2.1.jar]")

        when:
        instantRun("resolve1", "resolve2")

        then:
        fixture.assertStateLoaded()
        outputContains("result = [lib1-2.1.jar]")

        when:
        repo.publishNewVersion(this)
        instantRun("resolve1", "resolve2")

        then:
        fixture.assertStateStored()
        outputContains("Calculating task graph as configuration cache cannot be reused because file '${repo.versionMetadataLocation}' has changed.")
        outputContains("result = [lib1-2.5.jar, lib2-4.0.jar]")

        when:
        instantRun("resolve1", "resolve2")

        then:
        fixture.assertStateLoaded()
        outputContains("result = [lib1-2.5.jar, lib2-4.0.jar]")

        where:
        repo                | _
        new MavenFileRepo() | _
    }

    abstract class FileRepoSetup {
        abstract String getDisplayName()

        abstract String getProblemDisplayName()

        String getVersionMetadataLocation() {
            return 'maven-repo/thing/lib1/maven-metadata.xml'.replace('/', File.separator)
        }

        abstract String getPomLocation()

        abstract void setup(AbstractIntegrationSpec owner)

        abstract void publishWithDifferentArtifactContent(AbstractIntegrationSpec owner)

        abstract void publishWithDifferentDependencies(AbstractIntegrationSpec owner)

        abstract void publishNewVersion(AbstractIntegrationSpec owner)
    }

    class MavenFileRepo extends FileRepoSetup {
        @Override
        String getDisplayName() {
            return "Maven file repository"
        }

        @Override
        String getProblemDisplayName() {
            return 'maven'
        }

        @Override
        String getPomLocation() {
            return 'maven-repo/thing/lib1/2.1/lib1-2.1.pom'.replace('/', File.separator)
        }

        @Override
        void setup(AbstractIntegrationSpec owner) {
            owner.with {
                mavenRepo.module("thing", "lib1", "2.1").publish()
                buildFile << """
                    repositories {
                        maven {
                            url = '${mavenRepo.uri}'
                        }
                    }
                """
            }
        }

        @Override
        void publishWithDifferentArtifactContent(AbstractIntegrationSpec owner) {
            owner.with {
                mavenRepo.module("thing", "lib1", "2.1").publishWithChangedContent()
            }
        }

        @Override
        void publishWithDifferentDependencies(AbstractIntegrationSpec owner) {
            owner.with {
                def dep = mavenRepo.module("thing", "lib2", "4.0").publish()
                mavenRepo.module("thing", "lib1", "2.1").dependsOn(dep).publish()
            }
        }

        @Override
        void publishNewVersion(AbstractIntegrationSpec owner) {
            owner.with {
                def dep = mavenRepo.module("thing", "lib2", "4.0").publish()
                mavenRepo.module("thing", "lib1", "2.5").dependsOn(dep).publish()
            }
        }
    }

    class MavenLocalRepo extends FileRepoSetup {
        @Override
        String getDisplayName() {
            return "Maven local repository"
        }

        @Override
        String getProblemDisplayName() {
            return 'MavenLocal'
        }

        @Override
        String getPomLocation() {
            return 'maven_home/.m2/repository/thing/lib1/2.1/lib1-2.1.pom'.replace('/', File.separator)
        }

        @Override
        void setup(AbstractIntegrationSpec owner) {
            owner.with {
                m2.execute(executer)
                m2.mavenRepo().module("thing", "lib1", "2.1").publish()
                buildFile << """
                    repositories {
                        mavenLocal()
                    }
                """
            }
        }

        @Override
        void publishWithDifferentArtifactContent(AbstractIntegrationSpec owner) {
            owner.with {
                m2.execute(executer)
                m2.mavenRepo().module("thing", "lib1", "2.1").publishWithChangedContent()
            }
        }

        @Override
        void publishWithDifferentDependencies(AbstractIntegrationSpec owner) {
            owner.with {
                m2.execute(executer)
                def dep = m2.mavenRepo().module("thing", "lib2", "4.0").publish()
                m2.mavenRepo().module("thing", "lib1", "2.1").dependsOn(dep).publish()
            }
        }

        @Override
        void publishNewVersion(AbstractIntegrationSpec owner) {
            owner.with {
                m2.execute(executer)
                def dep = m2.mavenRepo().module("thing", "lib2", "4.0").publish()
                m2.mavenRepo().module("thing", "lib1", "2.5").dependsOn(dep).publish()
            }
        }
    }
}
/*
 * Copyright 2019 the original author or authors.
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

package promotion

import common.Os
import common.requiresNoEc2Agent
import common.requiresOs
import jetbrains.buildServer.configs.kotlin.v2019_2.AbsoluteId
import jetbrains.buildServer.configs.kotlin.v2019_2.BuildType
import jetbrains.buildServer.configs.kotlin.v2019_2.CheckoutMode

abstract class BasePromotionBuildType(vcsRootId: String, cleanCheckout: Boolean = true) : BuildType() {
    init {
        vcs {
            root(AbsoluteId(vcsRootId))

            checkoutMode = CheckoutMode.ON_AGENT
            this.cleanCheckout = cleanCheckout
            showDependenciesChanges = true
        }

        requirements {
            requiresOs(Os.LINUX)
            requiresNoEc2Agent()
        }

        params {
            param("env.GRADLE_INTERNAL_REPO_URL", "%gradle.internal.repository.url%")
            param("env.GE_GRADLE_ORG_GRADLE_ENTERPRISE_ACCESS_KEY", "%ge.gradle.org.access.key%")
        }
    }
}

/*
 * Copyright 2021 the original author or authors.
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

package org.gradle.internal.buildtree;

import org.gradle.api.internal.GradleInternal;
import org.gradle.internal.build.BuildLifecycleController;
import org.gradle.internal.build.BuildToolingModelAction;
import org.gradle.internal.build.BuildToolingModelController;

public class DefaultBuildTreeModelCreator implements BuildTreeModelCreator {
    private final BuildLifecycleController buildController;

    public DefaultBuildTreeModelCreator(BuildLifecycleController buildLifecycleController) {
        this.buildController = buildLifecycleController;
    }

    @Override
    public <T> void beforeTasks(BuildToolingModelAction<? extends T> action) {
        action.beforeTasks(new DefaultBuildToolingModelController());
    }

    @Override
    public <T> T fromBuildModel(BuildToolingModelAction<? extends T> action) {
        return action.fromBuildModel(new DefaultBuildToolingModelController());
    }

    private class DefaultBuildToolingModelController implements BuildToolingModelController {
        @Override
        public GradleInternal getConfiguredModel() {
            return buildController.getConfiguredBuild();
        }

        @Override
        public GradleInternal getMutableModel() {
            return buildController.getGradle();
        }
    }
}

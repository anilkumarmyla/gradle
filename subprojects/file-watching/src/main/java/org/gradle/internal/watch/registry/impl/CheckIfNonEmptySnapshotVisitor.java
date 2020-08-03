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

package org.gradle.internal.watch.registry.impl;

import org.gradle.internal.file.FileMetadata;
import org.gradle.internal.file.FileType;
import org.gradle.internal.snapshot.CompleteFileSystemLocationSnapshot;
import org.gradle.internal.snapshot.SnapshotHierarchy;

class CheckIfNonEmptySnapshotVisitor implements SnapshotHierarchy.SnapshotVisitor {
    private boolean empty = true;
    private boolean onlyMissing = true;

    @Override
    public void visitSnapshotRoot(CompleteFileSystemLocationSnapshot rootSnapshot) {
        if (rootSnapshot.getAccessType() == FileMetadata.AccessType.DIRECT) {
            empty = false;
            if (rootSnapshot.getType() != FileType.Missing) {
                onlyMissing = false;
            }
        }
    }

    public boolean isEmpty() {
        return empty;
    }

    public boolean containsOnlyMissingFiles() {
        return onlyMissing;
    }
}

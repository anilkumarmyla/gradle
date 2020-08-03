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

import org.gradle.internal.file.FileHierarchySet;
import org.gradle.internal.file.FileMetadata;
import org.gradle.internal.snapshot.CompleteDirectorySnapshot;
import org.gradle.internal.snapshot.CompleteFileSystemLocationSnapshot;
import org.gradle.internal.snapshot.FileSystemSnapshotVisitor;
import org.gradle.internal.snapshot.SnapshotHierarchy;

import java.util.function.Predicate;

class RemoveUnwatchedFiles implements FileSystemSnapshotVisitor {
    private SnapshotHierarchy root;
    private final Predicate<String> watchFilter;
    private final FileHierarchySet watchedDirectories;
    private final Invalidator invalidator;

    public RemoveUnwatchedFiles(SnapshotHierarchy root, Predicate<String> watchFilter, FileHierarchySet watchedDirectories, Invalidator invalidator) {
        this.root = root;
        this.watchFilter = watchFilter;
        this.watchedDirectories = watchedDirectories;
        this.invalidator = invalidator;
    }

    @Override
    public boolean preVisitDirectory(CompleteDirectorySnapshot directorySnapshot) {
        if (shouldBeRemoved(directorySnapshot)) {
            invalidateUnwatchedFile(directorySnapshot);
            return false;
        }
        return true;
    }

    private boolean shouldBeRemoved(CompleteFileSystemLocationSnapshot directorySnapshot) {
        return directorySnapshot.getAccessType() == FileMetadata.AccessType.VIA_SYMLINK
            || (watchFilter.test(directorySnapshot.getAbsolutePath()) && !isInWatchedDir(directorySnapshot));
    }

    private boolean isInWatchedDir(CompleteFileSystemLocationSnapshot snapshot) {
        return watchedDirectories.contains(snapshot.getAbsolutePath());
    }

    @Override
    public void visitFile(CompleteFileSystemLocationSnapshot fileSnapshot) {
        if (shouldBeRemoved(fileSnapshot)) {
            invalidateUnwatchedFile(fileSnapshot);
        }
    }

    @Override
    public void postVisitDirectory(CompleteDirectorySnapshot directorySnapshot) {
    }

    private void invalidateUnwatchedFile(CompleteFileSystemLocationSnapshot snapshot) {
        root = invalidator.invalidate(snapshot.getAbsolutePath(), root);
    }

    public SnapshotHierarchy getRootWithUnwatchedFilesRemoved() {
        return root;
    }

    public interface Invalidator {
        SnapshotHierarchy invalidate(String absolutePath, SnapshotHierarchy currentRoot);
    }
}

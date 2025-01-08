/*
 * Copyright © 2023 Github Lzhiyong
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

package x.code.app.filetree

import x.github.module.treeview.AbstractTree
import x.github.module.treeview.Tree
import x.github.module.treeview.TreeNodeGenerator
import x.github.module.treeview.TreeNode

import java.io.File

class FileNodeGenerator(
    private val rootPath: File,
    private val fileListLoader: FileListLoader
) : TreeNodeGenerator<File> {
    override suspend fun fetchChildData(targetNode: TreeNode<File>): Set<File> {
        val path = targetNode.requireData().absolutePath
        var files = fileListLoader.getCacheFileList(path)

        if (files.isEmpty()) {
            files = fileListLoader.loadFileList(path)
        }

        return files.toSet()
    }

    override fun createNode(
        parentNode: TreeNode<File>,
        currentData: File,
        tree: AbstractTree<File>
    ): TreeNode<File> {
        return TreeNode(
            data = currentData,
            depth = parentNode.depth + 1,
            name = currentData.name,
            id = tree.generateId(),
            hasChild = currentData.isDirectory && fileListLoader.getCacheFileList(currentData.absolutePath)
                .isNotEmpty(),
            isChild = currentData.isDirectory,
            expand = false
        )

    }

    override fun createRootNode(): TreeNode<File> {
        return TreeNode(
            data = rootPath,
            depth = -1,
            name = rootPath.name,
            id = Tree.ROOT_NODE_ID,
            hasChild = true,
            isChild = true,
        )
    }
}


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

import android.os.Parcelable
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.parcelize.Parcelize
import java.io.File

@Parcelize
class FileListLoader : Parcelable {
    
    private val cacheFiles: MutableMap<String, MutableList<File>> = mutableMapOf()
    
    private inline fun getSortedFileList(file: File): List<File> {
        return (file.listFiles() ?: emptyArray()).run {
            sortedWith { o1, o2 ->
                if (o1.isDirectory && o2.isFile) {
                    -1
                } else if (o1.isFile && o2.isDirectory) {
                    1
                } else {
                    o1.name.compareTo(o2.name)
                }
            }
        }
    }
    
    fun getCacheFileList(path: String) = cacheFiles[path] ?: emptyList()
    
    suspend fun loadFileList(
        path: String, 
        layer: Int = 0, 
        maxLayers: Int = 2
    ): List<File> = withContext(Dispatchers.IO) {
        return@withContext cacheFiles.getOrPut(path) {
            val files = getSortedFileList(File(path))
            val newLayer = layer + 1
            if (newLayer < maxLayers) {
                files.filter { it.isDirectory }
                    .map { directory ->
                        launch {
                            loadFileList(directory.absolutePath, newLayer)
                        }
                    }
            }
            return@getOrPut files.toMutableList()
        }
    }
    
    fun removeFileInCache(currentFile: File): Boolean {
        if (currentFile.isDirectory) {
            cacheFiles.remove(currentFile.absolutePath)
        }
        val parentFiles = cacheFiles[currentFile.parentFile?.absolutePath]
        return parentFiles?.remove(currentFile) ?: false
    }
    
    fun createFileInCache(file: File) {
        if (file.parentFile!!.isDirectory.not()) {
            throw RuntimeException("tried to create a file in a file")
        }
        cacheFiles[file.parentFile!!.absolutePath]!!.add(file)
    }
    
    fun renameFileInCache(oldFile: File, newFile: File) {
        removeFileInCache(oldFile)
        createFileInCache(newFile)
    }
}


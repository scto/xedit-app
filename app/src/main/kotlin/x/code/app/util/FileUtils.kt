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

package x.code.app.util

import java.io.File
import java.io.BufferedReader
import java.io.BufferedWriter
import java.io.InputStream
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.lang.Runtime

import java.text.DecimalFormat
import java.util.zip.CRC32

import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.zip.ZipFile
import java.util.zip.ZipInputStream
import java.util.zip.ZipEntry

object FileUtils {
    
    fun unzipFile(zipFile: File, extractDir: File) {    
        val zfile = ZipFile(zipFile)       
        val zinput = ZipInputStream(FileInputStream(zipFile))
        var entry: ZipEntry? = null
        
        while (zinput.nextEntry.also { entry = it } != null) {
            val output = File(extractDir, entry!!.name)
            if (output.parentFile != null && !output.parentFile.exists()) {
                output.parentFile.mkdirs()
            }

            if (!output.exists()) {
                if (entry!!.isDirectory) {
                    output.mkdirs()
                    continue
                } else {
                    output.createNewFile()
                }
            }
                       
            val bis = BufferedInputStream(zfile.getInputStream(entry))
            val bos = BufferedOutputStream(FileOutputStream(output))
            val buffer = ByteArray(1024)
            var len = bis.read(buffer)
            while (len >= 0) {
                bos.write(buffer, 0, len)
                // continue to read
                len = bis.read(buffer)
            }
            bos.close()
            bis.close()
        }
        // close the zip stream
        zfile.close()
    }

    // format document size
    fun formatSize(df: DecimalFormat, size: Long) = when {
        size < 1024 -> size.toString() + "B"
        size < 1048576 -> df.format(size / 1024f) + "KB"
        size < 1073741824 -> df.format(size / 1048576f) + "MB"
        else -> df.format(size / 1073741824f) + "GB"
    }

    // check file crc32
    fun checkCRC32(bytes: ByteArray): Long {
        return CRC32().apply { update(bytes) }.value
    }
}


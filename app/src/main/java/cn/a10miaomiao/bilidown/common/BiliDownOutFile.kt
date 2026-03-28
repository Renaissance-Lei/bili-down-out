package cn.a10miaomiao.bilidown.common

import android.net.Uri
import android.os.Environment
import java.io.File

class BiliDownOutFile(
    name: String,
) {

    private data class FileNameParts(
        val baseName: String,
        val extension: String,
        val suffixNumber: Int?,
    )

    companion object {
        const val DIR_NAME = "BiliDownOut"
        val downloadDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
        private val trailingSuffixRegex = Regex("^(.*)\\((\\d+)\\)$")

        fun getOutFolderUri(): Uri {
            return Uri.parse("content://com.android.externalstorage.documents/document/primary:Download%2f${DIR_NAME}")
        }

        fun getOutFolderPath(): String {
            return downloadDir.path + File.separator + DIR_NAME
        }

        private fun splitFileName(name: String): FileNameParts {
            val extensionIndex = name.lastIndexOf('.')
            val fileName = if (extensionIndex > 0) {
                name.substring(0, extensionIndex)
            } else {
                name
            }
            val extension = if (extensionIndex > 0) {
                name.substring(extensionIndex)
            } else {
                ""
            }
            val suffixMatch = trailingSuffixRegex.matchEntire(fileName)
            if (suffixMatch != null) {
                return FileNameParts(
                    baseName = suffixMatch.groupValues[1],
                    extension = extension,
                    suffixNumber = suffixMatch.groupValues[2].toInt(),
                )
            }
            return FileNameParts(
                baseName = fileName,
                extension = extension,
                suffixNumber = null,
            )
        }
    }

    private val outDir = File(downloadDir, DIR_NAME)

    init {
        if (!outDir.exists()){
            outDir.mkdir()
        }
    }

    var file = File(outDir, name)
        private set

    val path get() = file.path
    val name get() = file.name

    fun exists() = file.exists()

    fun autoRename(additionalUsedPaths: Set<String> = emptySet()) {
        val originalName = splitFileName(file.name)

        var newFile = file
        var count = originalName.suffixNumber ?: 0
        while (newFile.exists() || additionalUsedPaths.contains(newFile.path)) {
            count += 1
            val newName = "${originalName.baseName}($count)${originalName.extension}"
            newFile = File(outDir, newName)
        }
        file = newFile
    }
}

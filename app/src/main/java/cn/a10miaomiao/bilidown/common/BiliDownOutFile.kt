package cn.a10miaomiao.bilidown.common

import android.content.Context
import android.net.Uri
import android.os.Environment
import android.util.Log
import java.io.File

class BiliDownOutFile(
    name: String,
) {

    companion object {
        const val DIR_NAME = "BiliDownOut"
        val downloadDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)

        fun getOutFolderUri(): Uri {
            return Uri.parse("content://com.android.externalstorage.documents/document/primary:Download%2f${DIR_NAME}")
        }

        fun getOutFolderPath(): String {
            return downloadDir.path + File.separator + DIR_NAME
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
        val originalName = file.name
        val extensionIndex = originalName.lastIndexOf('.')
        val baseName = if (extensionIndex > 0) originalName.substring(0, extensionIndex) else originalName
        val extension = if (extensionIndex > 0) originalName.substring(extensionIndex) else ""

        var newFile = file
        var count = 1
        while (newFile.exists() || additionalUsedPaths.contains(newFile.path)) {
            val newName = "$baseName($count)$extension"
            newFile = File(outDir, newName)
            count++
        }
        file = newFile
    }
}
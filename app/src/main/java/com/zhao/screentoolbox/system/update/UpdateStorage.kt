package com.zhao.screentoolbox.system.update

import android.content.Context
import android.os.Environment
import com.zhao.screentoolbox.core.model.AvailableUpdate
import java.io.File

/** 集中约束下载目录，避免 FileProvider 暴露目录之外的路径。 */
internal object UpdateStorage {
    const val DIRECTORY_NAME = "ScreenToolboxUpdates"

    fun root(context: Context): File? = context
        .getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)
        ?.resolve(DIRECTORY_NAME)

    fun file(context: Context, update: AvailableUpdate): File? =
        root(context)?.resolve(update.fileName)

    fun isManagedFile(context: Context, file: File): Boolean {
        val root = root(context)?.canonicalFile ?: return false
        return file.canonicalFile.parentFile == root
    }
}
